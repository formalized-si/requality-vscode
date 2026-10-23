package com.unitesk.requality.eclipse.handlers;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.attribute.reference.RelationsTreeContext;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.formatversion.StoredFormatVersioning;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.ProjectRemovedWait;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.LocationHelper;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public class ProjectRenameChange extends Change {
  private String oldName, newName, localName;
  private RevisionInfo revision;
  private IProject pold = null, pnew = null;
  private Path path = null;
  public Map<TreeDB, URI> allRelated = null;
  private boolean ignoreMessages = true;
  public ProjectRenameChange(IProject pold,  String lname, String name, String newName) {
    this.pold = pold;
    if(pold!=null) {
      IPath loc = pold.getLocation();
      if(loc == null && pnew!=null)
        loc = pnew.getLocation();
      if(loc!=null)
        path = loc.toFile().toPath();
    }
    this.localName = lname;
    this.oldName = name;
    this.newName = newName;
  }
  public void setProjectNew(IProject p) {
    pnew = p;
  }

  @Override
  public String getName() {
    return "Rename "+oldName+" to "+newName;
  }
  
  public void setIgnoreMessages(boolean val)
  {
    ignoreMessages = val;
  }

  @Override
  public void initializeValidationData(IProgressMonitor pm) {
    
  }

  private Map<TreeDB, URI> toURIMap(Set<TreeDB> allRelated2) {
    Map<TreeDB, URI> ret = new HashMap<>();
    for(TreeDB db:allRelated2)
      ret.put(db, LocationHelper.getLocation(db, false));
    return ret;
  }

  @Override
  public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    return RefactoringStatus.create(Status.OK_STATUS);
  }

  @Override
  public Change perform(IProgressMonitor pm) throws CoreException {
    try {
      EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
      
      URI loc = null;
      if(pnew == null && pold!=null) {
        loc = pold.getLocationURI();
        if(loc!=null)
          try {
            pnew = et.getProject(pold.getLocationURI(), newName);
          }catch(Exception e) {
            e.printStackTrace();
          }
      }else if (pnew != null && loc == null)
        loc = pnew.getLocationURI();
      IProject proj = pnew == null ? pold : pnew;
      IFolder f = proj.getFolder(CoreUtils.CACHE_FOLDER_NAME);
      if(f!=null && f.exists())
        f.refreshLocal(IResource.DEPTH_INFINITE, null);

    } catch (CoreException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    Set<String> names = new HashSet<>();
    names.add(newName);
    names.add(oldName);
    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
    RelationsTreeContext related = CoreUtils.gatherRelatedTrees(
          et, null, new ConsoleProgressMonitor(), 1, names, false);
      allRelated = toURIMap(related.allRelated);
    boolean isDis = et.isDisabledListening(IResTracker.DISABLED_LIS_EVENTS);
    AtomicBoolean callRelatedClear = new AtomicBoolean(false);
    if(!isDis)
      et.disableListeners(IResTracker.DISABLED_LIS_EVENTS);
    URI loc = null;
    TreeDB dbold = null;
    AtomicBoolean dbfin = new AtomicBoolean(false);
    AtomicReference<TreeDB> dbnew = new AtomicReference<>(null);
    try {
      try {
        dbold = et.getTreeDB(pold, revision, localName);
      } catch (IOException e1) {
        RLogger.logError(e1);
      }
      TreeDB foldDB = dbold;
      
      loc = et.getUri(pold, true);
      RLogger.logDebug("before state "+localName+": "+et.getTreeStateManager().getState(localName, null), 3);
      RLogger.logDebug("before ploading "+localName+": "+et.getProjectLoadingState(loc), 3);
//      if(allRelated == null) {
//        Set<String> names = new HashSet<>();
//        names.add(newName);
//        names.add(oldName);
//        RelationsTreeContext related = CoreUtils.gatherRelatedTrees(
//           et, null, new ConsoleProgressMonitor(), 1, names, false);
//        allRelated = toURIMap(related.allRelated);
//      }
  //    Iterator<TreeDB>it = allRel.iterator();
  //    while(it.hasNext()) {
  //      TreeDB db = it.next();
  //      String pname = db.getProjectName();
  //      if(pname == null || pname.equals(oldName) || pname.equals(newName))
  //        it.remove();
  //    }
      TreeState[]states = new TreeState[] {TreeState.READY, TreeState.HALTED, TreeState.CANCELED};
      TreeStateManager tsm = et.getTreeStateManager();
      String fname = localName;
      AtomicBoolean isHookRemoved = new AtomicBoolean(false);
      IProject newProj = pnew;
      if(newProj == null)
        newProj = EclipseTracker.getProject(loc, newName);
      final TreeDB fdbold = dbold;
      AAllTreeStateChangeHandler handler = new AAllTreeStateChangeHandler() {//TODO from set?
          private void removeIfNeeded(TreeStateManager ftsm, String lname, TreeState[] fstates,
            IStateChangeHandler sch) {
              synchronized (isHookRemoved) {
                if(!isHookRemoved.get()) {
                  isHookRemoved.set(true);
                  ftsm.removeStateHook("*", fstates, sch);
                 }
              }
          }
          @Override
          public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState, TreeState state) {
            IStateChangeHandler sch = this;
            if(!pname.equals(newName))
              return;
            if(state == TreeState.READY) {
              if(db == null)
                return;
              URI loc = LocationHelper.getLocation(db, false);
              boolean isStoredCorrect = StoredFormatVersioning.getOne().storedVersionCorrect(loc);
              if(!isStoredCorrect)
                return;
              try {
                removeIfNeeded(tsm, lname, states, sch);
                URI ur = allRelated.get(db);
                if(ur == null)
                  ur = LocationHelper.getLocation(db, false);
                dbnew.set(db);
                callRelatedClear.set(true);
              }finally {
                synchronized (dbnew) {
                  dbfin.set(true);
                  dbnew.notifyAll();
                }
              } 
              Global.waitForFullClear(foldDB, dbnew.get(), et.getTreeStateManager(), allRelated, null);//TODO proper monitor
            }else {
              if(state == TreeState.CANCELED) {
                try {
                  removeIfNeeded(tsm, lname, states, sch);
                  callRelatedClear.set(true);
                }finally {
                  synchronized (dbnew) {
                    dbfin.set(true);
                    dbnew.notifyAll();
                  }
                }
              }
              else
                ProjectRemovedWait.registerWaitHalted(tsm, db, lname, ri, TreeState.LOADED_NO_CACHE, new Runnable() {
                  @Override
                  public void run() {
                    try {
                      removeIfNeeded(tsm, lname, states, sch);
                      callRelatedClear.set(true);
                    }finally {
                      synchronized (dbnew) {
                        dbfin.set(true);
                        dbnew.notifyAll();
                      }
                    }
                  }
                });
            }
          }
      };
      File renPath = haveNameChange(path, pold, fdbold, revision, localName, oldName, newName, allRelated);
      if(renPath!=null){
        ReentrantReadWriteLock l = tsm.getLock(newName);
        WriteLock wl = l.writeLock();
        wl.lock();
        try{
          if(tsm.isFullReady(newName, revision)){//TODO change?
            TreeDB db = null;
            try {
              db = et.getTreeDB(newProj, revision, newName);
            } catch (IOException e) {
              RLogger.logError(e);
            }
            isHookRemoved.set(true);//do not call hook remove
            handler.stateChanges(db, "*", newName, revision, TreeState.HALTED, TreeState.READY);
          }else{
            tsm.addStateHook("*", states, handler);
          }
        }finally{
          wl.unlock();
        }
      }else
        dbnew.set(fdbold);
      if(pnew == null)
        pnew = et.getProject(loc, newName);
      callNameChange(renPath, pold, pnew, fdbold, localName, oldName, newName, revision);
      RLogger.logDebug("after state: "+localName+" "+et.getTreeStateManager().getState(localName, null), 3);
      RLogger.logDebug("after ploading "+localName+" : "+et.getProjectLoadingState(loc), 3);
    }finally {
      if(!isDis)
        et.enableListeners(IResTracker.DISABLED_LIS_EVENTS);
    }
    if(pnew == null)
      pnew = et.getProject(loc, newName);
    ProjectRenameChange prc = new ProjectRenameChange(pnew, localName, newName, oldName);
    prc.setProjectNew(pold);
    return prc;
  }
  public static  Pair<Object,Map<TreeDB, URI>> NO_RENAME = new  Pair<Object,Map<TreeDB, URI>>(null,null); 
  public static File haveNameChange(Path path, IProject projectOld, TreeDB oldDB, RevisionInfo revision, String localName, String oldName, String newName,
      Map<TreeDB, URI> dbs) {
    //forget only actual rename..
    //DBNamesManager.DEFAULT.forget(DBLocalName.formLocalNameKey(localName,revision));//TODO mb fork
    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
    File pathf = null;
    if(path == null && projectOld!=null){
      IPath loc = projectOld.getLocation();
      if(loc!=null)
        path = loc.toFile().toPath();
    }
    if(oldDB == null)
      try {
        oldDB = et.getTreeDB(projectOld, null, localName);
      } catch (IOException e1) {
      } 
    if(path == null) {
      if(oldDB!=null){
        String pf = et.getLocation(oldDB, false);
        if(pf!=null)
          pathf = new File(URI.create(pf));
      }
    }else
      pathf = path.toFile();
//    if(path == null) {
//          p = ResourcesPlugin.getWorkspace().getRoot().getProject(oldName);
//          path = p.getLocation(); 
//    }
    String name = newName;
    if(name == null && pathf!=null) {
      name = CoreUtils.getProjectName(pathf);
    }
    
    if(pathf!=null && (name == null || !oldName.equals(newName))) {
      return pathf;
    }
    return null;//noRename
  }
  
  public void callNameChange(File pathf, IProject projectOld, IProject projectNew, TreeDB oldDB, 
      String localName, String oldName, String newName, RevisionInfo revision) {
    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
    et.callProjectRenameUpdate(pathf.toURI(), projectOld, projectNew, oldDB, revision, localName, oldName, newName);
  }

  @Override
  public Object getModifiedElement() {
    //TODO test
    return pnew;
  }

}
