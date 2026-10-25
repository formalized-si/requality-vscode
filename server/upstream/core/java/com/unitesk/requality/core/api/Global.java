package com.unitesk.requality.core.api;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.service.ResourcesServlet;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.storage.TreeEvent;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.storage.formatversion.StoredFormatVersioning;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.ProjectRemovedWait;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.repo.RepoManager;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.LocationHelper;
import com.unitesk.requality.tool.message.RMessages;

public class Global {
  private static List<IApiCacheClearListener> clearListeners = new LinkedList<>();
  private static List<IStoppable> stoppable = new LinkedList<>();
  public static void addCacheClearListener(IApiCacheClearListener one) {
    synchronized(Global.class) {
      clearListeners.add(one);
    }
  }
  public static boolean removeCacheClearListener(IApiCacheClearListener one) {
    synchronized(Global.class) {
      return clearListeners.remove(one);
    }
  }
  
  public static void clearCachesWithEvent(TreeDB treeDB, URI loc, boolean delete, boolean ignoreMessages) {
    URI location = loc!=null ? loc : LocationHelper.getLocation(treeDB,true);
    String pname = treeDB.getProjectName();
    IResTracker rt = treeDB.getTracker();
    if(location == null) {
      RLogger.logDebug("Caches are not cleared on "+pname+" because location is null");
      return;
    }
    clearCachesWithoutEvent(treeDB, location, delete, ignoreMessages);
    fireClearingEvent(treeDB, null,  location);
    CacheStorage.closeRecConnection(new File(location));//force close residual connections
    if(rt.getProjectLoadingState(location, pname)!=ProjectLoadingState.VERSION_UPDATE)
      rt.setProjectLoadingState(location, pname, ProjectLoadingState.NOT_LOADING);
  }
  public static void clearCachesWithoutEvent(TreeDB treeDB, URI loc, boolean delete, boolean ignoreMessages) {
    //may include reopen project
    IResTracker rt = treeDB.getTracker();
    String name = treeDB.getLocalName();
    String pname = treeDB.getProjectName();
    try{
      TreeStateManager tsm = rt.getTreeStateManager();
      if(tsm.getState(name, treeDB.revision)!=TreeState.RESTART)
        tsm.setState(name, pname, treeDB, TreeState.NOT_LOADED);
    }finally{
    }
    forgetRepoManager(name, treeDB, delete);
    SelectionManager.getOne().clearCaches(treeDB);
    for(IAPICacheProvider as:API.apiCache)
      as.clearCaches(treeDB, delete);
    TreeDB old = treeDB;
    
    Path rp = CoreUtils.getCacheDir(treeDB);
    if(rp!=null){
      rp = rp.resolve(ResourcesServlet.REPORT_FOLDER_NAME);
      CoreUtils.delete(rp.toFile(), true, false);
    }
    treeDB.clearCaches(delete, ignoreMessages);
    rt.clearCache(treeDB, name, delete);
    treeDB.setDisposed(false);//we need to fire events event for clear cache to avoid tree
  }
  public static void fireClearingEvent(TreeDB oldDb, TreeDB newDb, URI location) {
    List<IApiCacheClearListener> list = null;
    synchronized(Global.class) {
      list = new LinkedList<>(clearListeners);
    }
    for(IApiCacheClearListener cl: list)
      if(cl!=null)
        cl.cacheCleared(oldDb, newDb, location);  
  }
  public static TreeDB clearCachesAndReopenNonParallel(TreeDB treeDB, URI altLoc, boolean delete, boolean ignoreMessages){
    return clearCachesAndReopen(treeDB, altLoc, delete, ignoreMessages, true);
  }
  
  public static TreeDB clearCachesAndReopenParallel(TreeDB treeDB, URI altLoc, boolean delete, boolean ignoreMessages){
    return clearCachesAndReopen(treeDB, altLoc, delete, ignoreMessages, false);
  }
  public static void removeReportCacheFolders(String lname, String pname) {
    Path path = RepoManager.getRevisionFolder(lname);
    if(path==null)
      return;
    java.io.File f = path.toFile();
    if(f.exists()) 
      try {
        CoreUtils.delete(f, true, true);
      }catch(Exception e) {
        RLogger.logDebug(CoreUtils.getTrace(e));
      }
    RepoManager.forgetCPCaches(pname);
  }
  public static void forgetRepoManager(String lname, TreeDB db, boolean delete) {
    if(lname == null)
      lname = db.getLocalName();
    RepoManager rm = null;
    synchronized (man) {
      rm = man.remove(lname);
    }
    if(rm!=null) {
      for(TreeDB dba:rm.getTreeDBs())
        if(dba!=null)
          rm.clearCache(dba, lname, delete);
    }
  }
  private static TreeDB clearCachesAndReopen(TreeDB treeDB, URI altLoc, boolean delete, boolean ignoreMessages, boolean nonParallel){
    if(treeDB == null) {
      return null;
    }
    CoreUtils.lockWorkspace("clear caches for "+treeDB.getLocalName());
    String lname = null;
    String pname = null;
    IResTracker rt = treeDB.getTracker();
    URI loc = LocationHelper.getLocation(treeDB,false);
    if(loc == null)
      loc = altLoc;
    //TODO check
    if(treeDB.getProjectName() == null && loc!=null){//unloaded \ renamed tree
      try {
        TreeDB alt = rt.getTreeDB(loc, treeDB.revision, lname); 
        treeDB = alt;
      } catch (IOException e) {
        RLogger.logError(e);
      }
    }
    OperationResult or = OperationResult.UNSUCEEDED;
    try{
      RevisionInfo ri = TreeStateManager.getRevisionInfo(treeDB);
      lname = treeDB.getLocalName();
      pname = treeDB.getProjectName();
      Object o = rt.getStoringEntity(treeDB);
      TreeStateManager tsm = TreeStateManager.getOne(rt);
      
      tsm.resetCachesInitedState(rt, lname, ri);      
      
      WriteLock wl = tsm.getLock(lname).writeLock();
      wl.lock();
      try {
        TreeState ts = tsm.getState(lname, ri);
        if(TreeState.isIncorrectProjectState(ts)) {
          CoreUtils.unlockWorkspace();
          RLogger.logError("Tries to clear cache state for "+lname+" in inconsistant state "+ts);
          return null;
        }
        tsm.setStateWithoutDB(lname, pname, ri, TreeState.NOT_LOADED);
        //nondb to avoid ready-ready warninigs for calls on different trees
      } catch(Exception e) {
        CoreUtils.unlockWorkspace();
        throw e;
      } finally {
        wl.unlock();
      }
      //rt.setProjectLoadingState(loc, pname, ProjectLoadingState.START_TRACKING);
     // rt.disableNewListeners();
      try {
          TreeStateManager tsm2 = tsm;
          tsm2.setState(lname, pname, treeDB, ri, TreeState.RESTART);//treeDB instance outdated!
          final TreeDB ftreeDB = treeDB;
          TreeState[]states = new TreeState[] {TreeState.READY, TreeState.HALTED, TreeState.CANCELED};
          String fpname = pname;
          final URI floc = loc;
          IStateChangeHandler hook = new AAllTreeStateChangeHandler() {
            boolean removed = false;
            private void removeIfNeeded(TreeStateManager ftsm, String lname, TreeState[] fstates,
              IStateChangeHandler sch) {
                synchronized (this) {
                   if(!removed) {
                    removed = true;
                    ftsm.removeStateHook(lname, fstates, sch);
                   }
                }
            }
            @Override
            public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
                TreeState oldState, TreeState state) {
              IStateChangeHandler sch = this;
              if(state == TreeState.READY) {
                if(StoredFormatVersioning.getOne().storedVersionCorrect(floc)) {
                  if(db!=null) 
                    rt.notifyListeners(Moment.BEFORE, lname, pname, db, TreeEvent.TREE_OPEN);//Update state on
                  removeIfNeeded(tsm2,lname,states,this);
                  CoreUtils.unlockWorkspace();
                  rt.setProjectLoadingState(floc, pname, ProjectLoadingState.LOADED);
                  if(db!=null) 
                    rt.notifyListeners(Moment.AFTER, lname, pname, db, TreeEvent.TREE_OPEN);//Update state on
                }else
                  RLogger.logDebug("Loading project "+lname+" and it seems to have old version, wait for another update after version to be correct");
               // rt.enableNewListeners();
              }else {
                if(state == TreeState.CANCELED) {
                  removeIfNeeded(tsm2,lname,states,this);
                  CoreUtils.unlockWorkspace();
                }else
                  ProjectRemovedWait.registerWaitHalted(tsm, db, lname, ri, TreeState.READY, new Runnable() {
                    @Override
                    public void run() {
                      removeIfNeeded(tsm2,lname,states,sch);
                      CoreUtils.unlockWorkspace();
                      rt.setProjectLoadingState(floc, pname, ProjectLoadingState.NOT_LOADING);
                    }
                  });
              }
            }
        };

        tsm2.addStateHook(lname, states, hook);
        boolean isEn = !rt.isDisabledListening(IResTracker.DISABLED_NEW_PROJECTS);
        if(isEn)
          rt.disableListeners(IResTracker.DISABLED_NEW_PROJECTS);
        try {
          clearCachesWithEvent(treeDB, loc, delete, ignoreMessages);
          //TODO fire cacche cleared event after?
          or = delete?OperationResult.SUCEEDED: reloadProject(treeDB, altLoc!=null?altLoc:loc, ignoreMessages, nonParallel);
        }finally {
          if(isEn)
            rt.enableListeners(IResTracker.DISABLED_NEW_PROJECTS);
        }
        try {
          rt.refresh(o, -1);
        } catch (Exception e1) {
          e1.printStackTrace();
        }
        //TODO was it needed?
//        //rt.open(loc);
//        TreeDB db = rt.loadTreeDBDescription(loc, ri, lname, treeDB.getMonitor());
//        or = rt.trackTreeDB(loc, lname, db.getProjectName(), false, false, true, true, db);
//        rt.close(o);
//        rt.open(o);
//        TreeDB db = rt.loadTreeDBDescription(o, ri, lname, treeDB.getMonitor());//TODO IDE monitor
//        or = rt.trackTreeDB(o, lname, null, false, nonParallel, true, true, db);
        if (or != OperationResult.PARALLEL) {
          TreeDB newProj = rt.getTreeDB(altLoc!=null?altLoc:loc, ri, fpname);
          hook.stateChanges(newProj, lname, pname, ri, 
           null, (or == OperationResult.SUCEEDED||or == OperationResult.LOAD_AS_IT)? 
                  TreeState.READY:
            TreeState.DISPOSED);
          return newProj;
        }
        return null;
      }finally {
      }
    }catch(Exception e) {
      if(lname == null)
        lname = "NULL";
      RMessages.openError("Unsuccesfull project cache reloading", "Cannot reload project '"+lname+"' because of problem", CoreUtils.getTrace(e), ignoreMessages);
    }finally{
    }
    return null;
  }

  private static OperationResult reloadProject(TreeDB treeDB, URI loc, boolean ignoreMessages, boolean nonParallel) {
    IResTracker rt = treeDB.getTracker();
    //replaced by actual reinit
//    ITreeStorage storage = treeDB.getStorage();
//    if(storage.getMonitor().isCanceled() || storage.getCacheStorage().isDisconnected())//outdated monitor, can be on mem storage
//      storage.renew();
    String lname = treeDB.getLocalName();
    String pname = treeDB.getProjectName();
    TreeStateManager tsm = rt.getTreeStateManager();
    tsm.setStateWithoutDB(lname, pname, treeDB.revision, TreeState.RESTART);
    if(rt.getProjectLoadingState(loc, pname)!=ProjectLoadingState.VERSION_UPDATE)
      rt.setProjectLoadingState(loc, treeDB.getProjectName(), ProjectLoadingState.NOT_LOADING);
    return rt.handleNewProject(loc, lname, !nonParallel, ignoreMessages);
  }

  static Map<String, RepoManager> man = new HashMap<>();
  public static RepoManager getRepoManager(TreeDB db) {
    db = Instantiator.getRootTree(db);
    if(db == null)
      return null;
    String lname = db.getLocalName();
    RepoManager rm = null;
    synchronized(man) {
      rm = man.get(lname);
    }
    if(rm == null) {
      synchronized(man) {
        RepoManager alt = man.get(lname);
        if(alt==null) {
          rm = new RepoManager(db);//TODO possible lock?
          man.put(lname, rm);
        }else
          rm = alt;
      }
    }
    if(rm.getTreeDB().isDisposed()||rm.getTreeDB()!=db) {
      //TODO clear old rm cache?
      //.clearCache(rm.getTreeDB(), lname, false);
      if(rm.testCVSClientChanged(db))
        synchronized(man) {//TODO another alt?
          rm = new RepoManager(db);
          man.put(lname, rm);
        }
    }
    return rm;
  }

  public static void clearNodeCache(
      TreeDB db, TreeNode node, boolean del) {
    ResManager.getManager().getTracker().clearNodeCache(db, node, del);
  }
  public static void registerStoppable(IStoppable s){
    synchronized (stoppable) {
      stoppable.add(s);
    }
  }
  public static void unRegisterStoppable(IStoppable s){
    synchronized (stoppable) {
      stoppable.remove(s);  
  }
  }

  public static void callStop() {
  List<IStoppable> l = null;
  synchronized (stoppable) {
    l = new LinkedList<>(stoppable);
  }
    for(IStoppable s: l)
      s.stop();
  }
  public interface ICheckerContainerProvider{
    public CheckerContainer getCheckerContainer(TreeDB db, RevisionInfo ri);
  }
  public static ICheckerContainerProvider checkProvider = new ICheckerContainerProvider(){
    @Override
    public CheckerContainer getCheckerContainer(TreeDB db, RevisionInfo ri) {
      return CheckerContainer.getOne(db, ri);
    }
  };
  public static CheckerContainer getCheckersContainer(TreeDB treeDB, RevisionInfo ri) {
    return checkProvider.getCheckerContainer(treeDB, ri);
  }

  public static void runCheckers(TreeDB db,boolean tree, boolean nontree) {
    CheckerContainer container = db.getCheckerContainer();    
    if(nontree || tree) {
      container.stopOldThread(false);
      CacheStorage cs = db.getTracker().getCPCacheStorage(db);
      CacheTransactionDescription ctd = null;
      try {
        ctd = cs.startCacheTransaction("Run checkers on "+db.getSemiLocalName(), 2);
        DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(db.getNode(Requirement.getTypeRootQId()));
        Iterator<TreeNode> it = dftw.iterator();
        while(it.hasNext()){
          TreeNode node = it.next();
          ReentrantLock rl = node.getTreeCheckersLock();
          try {
            rl.lock();
            if(tree)
              node.clearTreeCheckerProblems();
            if(nontree) 
              node.clearChecked();
            db.getCheckerContainer().addToCheckQ(node,false);
          }finally {
            rl.unlock();
          }
        }
      }finally {
        if(ctd != null && ctd.error==null)
          cs.commit(ctd);
      }
    }
    container.markRunTreeCheck();
  }
  public static void waitForFullClear(TreeDB treeOld, TreeDB treeNew, TreeStateManager tsm, Map<TreeDB, URI> dbsp, IProgressMonitor mon) {
    if(dbsp == null || dbsp.isEmpty())
      return;
    Map<TreeDB, URI> dbs = new HashMap<>(dbsp);//clone
    AtomicReference<Object> refTarget = new AtomicReference<>(dbs);

    TreeState[]states = new TreeState[] {TreeState.READY, TreeState.HALTED,  TreeState.CANCELED};
    //String lname = startTree.getLocalName();
    
    dbs.remove(treeOld);
    dbs.remove(treeNew);
    
    LinkedList<TreeDB> readyWait = new LinkedList<>();
    //readyWait.add(db);
    dbs.entrySet().forEach(one->{if(!readyWait.contains(one.getKey())) {readyWait.add(one.getKey());}});
    AtomicBoolean ended = new AtomicBoolean(false);
    AtomicBoolean isHookRemoved = new AtomicBoolean(false);
    IStateChangeHandler hook = new AAllTreeStateChangeHandler() {//TODO from list?
      private void removeIfNeeded(TreeStateManager ftsm, String lname, TreeState[] fstates,
        IStateChangeHandler sch) {
          synchronized (isHookRemoved) {
             if(!isHookRemoved.get()) {
              isHookRemoved.set(true);
              ftsm.removeStateHook(lname, fstates, sch);
             }
          }
      }
      private void runOnNextDB() {
        TreeDB dbb = null;
        synchronized (readyWait) {
          if(!readyWait.isEmpty())
            dbb = readyWait.removeFirst();
        }
        TreeDB reloaded = dbb;
        while(reloaded!=null) {
          String dblname = reloaded.getLocalName();
          tsm.addStateHook(dblname, states, this);
          isHookRemoved.set(false);
          reloaded = Global.clearCachesAndReopenNonParallel(dbb, null, false, true);
          if(reloaded == null) {
            tsm.removeStateHook(dblname, states, this);
            isHookRemoved.set(true);
          }
          synchronized (readyWait) {
            if(!readyWait.isEmpty()) {
              dbb = readyWait.removeFirst();
              reloaded = dbb;
            }else
              reloaded = null;
          }
          
        }

        boolean empty = false;
        synchronized (readyWait) {
          empty = readyWait.isEmpty(); 
        }
        if(empty)
          synchronized(ended){
            ended.set(true);
            synchronized (refTarget.get()) {
              refTarget.get().notifyAll();
            }
          }
      }
      @Override
      public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState, TreeState state) {
        IStateChangeHandler sch = this;
        //what if called after git operations?
        if(state == TreeState.CANCELED) {
          removeIfNeeded(tsm, lname, states, sch);
          runOnNextDB();
        }else
        if(state == TreeState.HALTED) {
          ProjectRemovedWait.registerWaitHalted(tsm, db, lname, db.revision, TreeState.LOADED_NO_CACHE, new Runnable() {
           @Override
            public void run() {
              removeIfNeeded(tsm, lname, states, sch);
              runOnNextDB();
            }
          });
        }
        if(state == TreeState.READY) {
          removeIfNeeded(tsm, lname, states, sch);
          runOnNextDB();
            //wait for next cache clear
        }
      }
    };
    //tsm.addStateHook(lname, states, hook );

   // Global.clearCachesAndReopenNonParallel(startTree, null, false, true);
    //tree was already cleared
    hook.stateChanges(treeNew, treeNew.getLocalName(), treeNew.getProjectName(), treeNew.revision, TreeState.HALTED, TreeState.READY);
    try {
      boolean cond = false;

      synchronized (ended) {
        cond = !ended.get();
      }
      while(cond) {
        synchronized (refTarget.get()) {
          refTarget.get().wait(1000);//TODO replace by wait
          cond = !ended.get();
        }
      }
    } catch (InterruptedException e) {
      RLogger.logError(e);
    }
  }
}
