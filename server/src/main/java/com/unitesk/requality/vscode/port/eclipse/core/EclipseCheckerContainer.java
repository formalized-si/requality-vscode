package com.unitesk.requality.vscode.port.eclipse.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.unitesk.requality.vscode.ui.resources.ResourceException;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.runtime.Job;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.api.IStoppable;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.checker.CheckerProblem;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.checker.INodeMark;
import com.unitesk.requality.core.checker.ITreeCheckerProblem;
import com.unitesk.requality.core.checker.ITreeMark;
import com.unitesk.requality.core.checker.IWarningMark;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.CheckerCNFMenuBuilder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;

public class EclipseCheckerContainer extends CheckerContainer {
  public EclipseCheckerContainer(TreeDB db) {
    super(db);
  }
//  
//  @Override
//  protected void startNewThread() {
//    
//  }
//  @Override
//  protected void stopOldThread() {
//    
//  }
  public static final String REQUALITY_MARKER_TYPE = "com.unitesk.requality.marker";

  public static final String REQUALITY_MARKER_WARN_TYPE = "com.unitesk.requality.markerWarn";

  protected final Thread wjKiller = new Thread(new Runnable() {
    @Override
    public void run() {
      halted = true;
    }
  });

  private Map<TreeDB, ReentrantReadWriteLock> rwl = new ConcurrentHashMap<>();
  private CopyOnWriteArraySet<TreeNode> uplist = new CopyOnWriteArraySet<>();
  private Set<String> uplistDB = new CopyOnWriteArraySet<>();
  private boolean registered = false;
  WorkspaceJob wj = null;
  boolean halted = false;

  public static EclipseCheckerContainer getOne(TreeDB db, RevisionInfo ri){
    return RequalityPlugin.getCheckers(db, ri);
  }
  
  @Override
  public void virtualErrorsChanged(TreeNode node){
    if(node.isDisposed())
      return;//removed node
    synchronized (uplist) {
      uplist.add(node);
      String name = node.getTreeDB().getLocalName();
      if(!uplistDB.contains(name))
        uplistDB.add(name);
    }
    if(wj==null){
      TreeStateManager tsm = TreeStateManager.getOne(node.getTreeDB().getTracker());
      wj = new WorkspaceJob("Update virtual error status for projects ("+toString(uplistDB)+")"){
      @Override
      public boolean shouldRun() {
        if(halted)
          return false;
        if(!super.shouldRun())
          return false;
        for(String name: uplistDB) {
          if(tsm.getState(name,null)!=TreeState.READY) 
            return false;
        }
        return true;
      }
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {  
        try{
          boolean ise = false;
          synchronized (uplist) {
            ise = uplist.isEmpty();
          }
          while(!ise && !halted){
            TreeNode node = null;
            synchronized (uplist) {
              ise = uplist.isEmpty();
              if(ise)
                node = null;
              else{
                node = uplist.iterator().next();
                uplist.remove(node);
              }
            }
            if(node==null || node.isDisposed()){
              continue;
            }
            if((tsm.getState(node.getTreeDB().getSemiLocalName(),null)!=TreeState.READY) || !node.getTreeDB().getStorage().canLoad(node.getUUId()))
              continue;
            IMark[] marks = node.getCheckerProblems();
            updateMarks(node,marks,true);
            if(ise){
              try {
                EclipseCheckerContainer.this.wait(2000);
              } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                RLogger.logError(e);
              }
              synchronized (uplist) {
                ise = uplist.isEmpty();
              }
            }
          }
          synchronized (uplist) {
            uplistDB.clear();
            wj = null;
          }
        }catch(Exception e){
          RLogger.logError("Problem with checkers on "+uplistDB.toArray()+" \t"+CoreUtils.getTrace(e));
        }
        finally {
          synchronized (EclipseCheckerContainer.this) {
            Runtime.getRuntime().removeShutdownHook(wjKiller);
            registered = false;
          }
        }
        return Status.OK_STATUS;
      }
    };
    synchronized(EclipseCheckerContainer.this) {
      if(!registered) {
        Runtime.getRuntime().addShutdownHook(wjKiller);
        registered = true;
      }
    }
    if(wj!=null)
      wj.schedule();//if null seems that it is already checked
    }
  }

  private String toString(Collection<String> list) {
    StringBuilder sb = new StringBuilder();
    for(String db:list){
      if(sb.length()!=0)
        sb.append(", ");
      sb.append(db);
    }
    return sb.toString();
  }
  
  private Map<String, IMark[]> oldMarks = new HashMap<>();
  
  private boolean updateMarks(TreeNode node, IMark[] marks, boolean strictLock) {
    IResource r = null;
    try{
      r = EclipseTracker.getMarkerResourceByNode(node, strictLock?LockType.LOCK_WAIT:LockType.TRY_LOCK);
    }
    catch(LockIsBusy lib){
      RLogger.logDebug("Resource lock cannot be get for error state operation on "+node.getUUId(),7);
      return false;
    }
    if(r!=null&&r.exists()){
      //TODO remove locks?
      synchronized (ResourceStorage.getLockObject(r.getProject(),node.getUUId())) {
       if(!r.exists())
         return true;//project was deleted
       return addToMarksUpdate(node, r, marks);
      }
    }
    return true;
  }
  LinkedList<UUID> updateCallList = new LinkedList<UUID>();
  Map<UUID, MarkUpdateCall> calls = new HashMap<>();
  private class MarkUpdateCall{
    public TreeNode node;
    public IResource r;
    public IMark[] marks;
  }
  private boolean addToMarksUpdate(TreeNode node, IResource r, IMark[] marks) {
    String path = r.getFullPath().toString();
    IMark[] marksNow = oldMarks.get(path);
    if(marks == null || marks.length == 0) {
      if(marksNow == null || marksNow.length == 0) {
        oldMarks.put(path, marks);
        return false;
      }
    }
    if(marksNow== null && marks==null || marksNow!=null && marksNow.equals(marks)) {
      return false;//no chnages?
    }
    MarkUpdateCall muc = new MarkUpdateCall();
    muc.node = node;
    muc.r = r;
    muc.marks = marks;
    synchronized (calls) {
      updateCallList.remove(node.getUUId());
      updateCallList.addLast(node.getUUId());
      calls.put(node.getUUId(), muc);
      startThreadIfNo(node.getTreeDB());
    }
    return muc.marks==null || muc.marks.length==0;
  }
  RunnableWithMonitor rwm = null;
  private void startThreadIfNo(TreeDB db) {
    synchronized (calls) {
      if(rwm == null) {
        rwm = new RunnableWithMonitor() {
          @Override
          public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
            while(!updateCallList.isEmpty()) {
              if(db.isDisposed())
                break;
              MarkUpdateCall muc = null;
              synchronized (calls) {
                if(updateCallList.isEmpty())
                  break;
                UUID uid = updateCallList.removeFirst();
                muc = calls.remove(uid);
              }
              if(muc == null)
                continue;
              try {
                muc.r.deleteMarkers(REQUALITY_MARKER_WARN_TYPE, true, IResource.DEPTH_ZERO);
              } catch (CoreException e) {
                // TODO Auto-generated catch block
                RLogger.logError(e);
              } 
              try {
                muc.r.deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
              } catch (CoreException e) {
                // TODO Auto-generated catch block
                RLogger.logError(e);
              } 
              if(muc.marks != null && muc.r.exists())
                for (IMark problem : muc.marks) {
    //                  if(node!=problem.getTarget())
    //                    RLogger.logError("Target of "+problem.toString()+" is different from node where problem apeears "+node.getUUId()); 
              //TreeNode node = (TreeNode) problem.getTarget();
                  try {
                    IMarker marker;
                    if(problem instanceof IWarningMark)
                      marker = muc.r.createMarker(REQUALITY_MARKER_WARN_TYPE);
                    else if (problem instanceof IMark)
                      marker = muc.r.createMarker(REQUALITY_MARKER_TYPE);
                    else marker = muc.r.createMarker(REQUALITY_MARKER_WARN_TYPE);
                    //marker= resource.createMarker(REQUALITY_MARKER_WARN_TYPE);
                    if (marker.exists()) { //TODO why it can not exist?
                      try{
                        marker.setAttribute(IMarker.MESSAGE, problem.getDescription());
                        if (problem instanceof CheckerProblem)
                          marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
                        else marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
                        if (muc.node.getType().equals(Document.TYPE_NAME))
                          marker.setAttribute(IMarker.LOCATION, ((Document) muc.node).getHTMLPath());
                        else marker.setAttribute(IMarker.LOCATION, muc.node.getQualifiedId());
                      }
                      catch(ResourceException e){
                        //RLogger.logDebug("Marker change causes res not found - marker removed "+marker.getId()+" "+marker.getType());
                      }
                      catch(Exception e){
                        RLogger.logError(e);
                      }
                    } else
                      RLogger.logInfo("non ex. marker");
                  } catch (CoreException e) {
                    RLogger.logError(e);
                  }
                }
              oldMarks.put(muc.r.getFullPath().toString(), muc.marks);
            }
            synchronized (calls) {
              rwm = null;
            }
          }
        };
        JobManager.jm.startJob("Apply problems on "+db.getSemiProjectName(), rwm, true);
      }
    }
  }
  private Map<TreeNode, Integer> last = new HashMap<>();
  @Override
  protected void applyProblemsInfo(TreeNode node, ArrayList<IMark> result) {
    super.applyProblemsInfo(node, result);//TODO maybe here?
    if(!last.containsKey(node)||last.get(node)!=result.hashCode())
    {
      Map<TreeNode, LinkedHashSet<IMark>> toupdate = null;
      for(IMark m:new ArrayList<>(result)){
        TreeNode[] nde = gatherNodes(m, node.getTreeDB());
        boolean ignore = true;
        for(TreeNode n:nde){
          if(n!=null && n.getSUUId().equals(node.getSUUId())){
            ignore = false;
          }else if(n!=null){
            if(toupdate==null)
              toupdate = new HashMap<>();
            if(!toupdate.containsKey(n))
              toupdate.put(n, new LinkedHashSet<>());
            toupdate.get(n).add(m);
          }
       }
       if(ignore)
         result.remove(m);
      }
      if(toupdate!=null){
        for(Entry<TreeNode, LinkedHashSet<IMark>> en:toupdate.entrySet()){
          TreeNode nd = en.getKey();
          ReentrantLock rl = nd.getTreeCheckersLock();
          IMark[]mp = null;
          try {
           rl.lock();
             nd.removeTreeCheckerProblems(ITreeCheckerProblem.class);
             mp = nd.getTreeCheckerProblems();
            LinkedHashSet<IMark> toe = en.getValue();
            int i = 0;
            if(mp == null)
              mp = new IMark[toe.size()];
            else{
              for(IMark m2:mp)
                toe.remove(m2);//TODO prev results?
              i = mp.length;
              IMark[]clone = new IMark[mp.length+toe.size()];
              int j = 0;
              for(IMark ma:mp)
                clone[j++] = ma;
              mp = clone;
            }
            if(toe.size()>0){
              nd.registerTreeCheckerProblems(toe);
              //addToCheckQ(nd);//TODO add to refresh only
            }
          } finally {
            rl.unlock();
          }
        }        
      }
      if(!last.containsKey(node)||last.get(node)!=result.hashCode()){
        last.put(node, result.hashCode());
        virtualErrorsChanged(node);
      }
    }
  }

  private TreeNode[] gatherNodes(IMark m, TreeDB db) {
    TreeNode[] ret = new TreeNode[0];
    if(m == null)
      return ret;
    if(m instanceof ITreeMark)
    {
      ITreeMark tm = (ITreeMark) m;
      int i = 0;
      ret = new TreeNode[tm.getTargets().length];
      for(Object o:tm.getTargets())
        if(o instanceof UUID)
        {
          TreeNode node = db.getNode((UUID)o);
          if(node!=null)
            ret[i++] = node;
        }else
          if(o instanceof TreeNode)
            ret[i++] = (TreeNode) o;
      return ret;
    }
    if(m instanceof INodeMark){
      INodeMark nm = (INodeMark) m;
      if(nm.getTarget() instanceof UUID){
        TreeNode rn = db.getNode((UUID)nm.getTarget());
        if(rn!=null)
        {
            ret = new TreeNode[1];
            ret[0] = rn;
        }    
      } else
        if(nm.getTarget() instanceof TreeNode){
            ret = new TreeNode[1];
            ret[0] = (TreeNode) nm.getTarget();      
        }
    }
    return ret;
  }

  @Override
  public void stopOldThread(boolean haltedState) {
    halted = haltedState || halted;
    Object foldcheckthread;
    long oldid = -1;
    IStoppable st = null;
    synchronized (this) {
      foldcheckthread = checkthread;
      oldid = checkthreadid;
      st = checkStopper;
    }
    if(foldcheckthread!=null && foldcheckthread instanceof WorkspaceJob){
      resetCheckerThreadVariables();
      synchronized(EclipseCheckerContainer.this){
        try{
          EclipseCheckerContainer.this.notifyAll();
        }catch(IllegalMonitorStateException ir){
          ir.printStackTrace();
        }
      }
      ((WorkspaceJob)foldcheckthread).cancel();
    }else
      resetCheckerThreadVariables();
  }
  
  @Override
  protected void startNewThread() {
    WorkspaceJob wd = null;
    synchronized (EclipseCheckerContainer.this) {
      if(checkthread != null || checkthreadid != -1)
        stopOldThread(false);
      if(halted)
        return;
      halted = false;
      final TreeDB baseDB = curDB;
      if(curDB.isDisposed() || curDB == null)
        return;//drop attempt to check disposed nodes
      AtomicReference<WorkspaceJob> wdr = new AtomicReference<>();
      AtomicReference<IStoppable> arr = new AtomicReference<>();
      wd = new WorkspaceJob("Checking nodes for "+curDBName){
       @Override
       public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
         WorkspaceJob wj = wdr.get();
         IStoppable ist = arr.get();
         try {
           synchronized (EclipseCheckerContainer.this) {
             if(checkthread == null || !(checkthread instanceof WorkspaceJob) || checkthread!=this || baseDB!=curDB)
               return Status.CANCEL_STATUS;
           }
           boolean canRun = CoreUtils.getWorkspaceOperation().isEmpty();
           boolean emp = false;
           synchronized (nodesTo) {
             emp = nodesTo.isEmpty();
           }
           boolean halt = false;
           boolean treeLoading = false;
           while(!monitor.isCanceled() && !emp && (baseDB == curDB && curDB!=null && !curDB.isDisposed()) && !halt){
             canRun = CoreUtils.getWorkspaceOperation().isEmpty();
             synchronized (EclipseCheckerContainer.this) {
               if(checkthread == null || !(checkthread instanceof WorkspaceJob) || checkthread!=this || baseDB!=curDB)
                 break;
             }
             if(canRun){
               synchronized (nodesTo) {
                  emp = nodesTo.isEmpty();
                }
               treeLoading = !canCheck(curDB);
               canRun = (!emp && !treeLoading);
             }
             if(canRun)
               canRun = CoreUtils.getWorkspaceOperation().isEmpty();
             if(monitor.isCanceled())
               return Status.CANCEL_STATUS;
             if(canRun&&checkthread == this) {
               com.unitesk.requality.core.operation.IProgressMonitor coreMon = new EclipseProgressMonitor(monitor);
               halt = !runCheckerThreadActions(true, coreMon) || curDB.getStorage().getMonitor().isCanceled();
             }
             else
              try {
                Thread.currentThread().join(treeLoading?10000:2000);
              } catch (InterruptedException e1) {
                RLogger.logError(e1);
              }
              if(checkthread != this)
                halt = true;
              if(halt)
                break;
            synchronized (nodesTo) {
              emp = nodesTo.isEmpty();
            }
            treeLoading = !canCheck(curDB);
            canRun = (!emp && !treeLoading);
            if(emp)
              try {
                Thread.currentThread().join(2000);
              } catch (InterruptedException e1) {
                RLogger.logError(e1);
              }
            if(checkthread != this)
              break;
            synchronized (nodesTo) {
              emp = nodesTo.isEmpty();
            }
            canRun = (!emp && canCheck(curDB));
          }
          if(checkthread == this){
            stopOldThread(false);
          }
        }finally {
          Global.unRegisterStoppable(ist);
        }
        return Status.OK_STATUS;
      }
        private boolean canCheck(TreeDB curDB) {
          TreeStateManager tsm = TreeStateManager.getOne(curDB.getTracker());
          TreeState ts = tsm.getState(curDB.getLocalName(),tsm.getRevisionInfo(curDB));
          return ts == TreeState.READY;
        }
      };
      wdr.set(wd);
      IStoppable st = new IStoppable(){
        @Override
        public void stop() {
          Global.unRegisterStoppable(this);
          if(checkthread instanceof WorkspaceJob && checkthread == wj){
            stopOldThread(false);
          }
          if(wj!=null) {
            wj.cancel();
            synchronized (EclipseCheckerContainer.this) {
              try {
                EclipseCheckerContainer.this.notifyAll();
              }catch(Exception e) {
                RLogger.logError(e);
              }
            }
          }
        }
      };
      arr.set(st);
      if(checkStopper!=null)
        Global.unRegisterStoppable(st);
      checkStopper = st;
      Global.registerStoppable(st);
      checkthread = wd;
    }
    if(checkThreadStarted == null) {
      checkThreadStarted = checkthread;
      wd.setUser(false);
      wd.setPriority(Job.LONG);
      wd.schedule();
    }
  }

  @Override
  public boolean isEnabled() {
    IProject p = EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProject(getDB());
    if(p == null)
      return super.isEnabled();
    try {
      return CheckerCNFMenuBuilder.getCheckersEnabled(p);
    } catch (CoreException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    return false;
    //PlatformUI.getWorkbench().getRe
  }
  @Override
  public void registerProblems(TreeNode treeNode, IMark[] problems) {
    updateMarks(treeNode,problems, true);
  }
}
