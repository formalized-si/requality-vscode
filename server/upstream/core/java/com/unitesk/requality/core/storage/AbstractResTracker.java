package com.unitesk.requality.core.storage;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.api.IAPICacheProvider;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.undoredo.UndoRedoMech;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.storage.cache.ICacheStorageProvider;
import com.unitesk.requality.core.storage.cache.SettingsManipulator;
import com.unitesk.requality.core.storage.formatversion.StoredFormatVersioning;
import com.unitesk.requality.core.storage.formatversion.StoredFormatVersioning.ConversionResult;
import com.unitesk.requality.core.storage.formatversion.VersionUpdateInfo;
import com.unitesk.requality.core.transaction.ITransactionListener;
import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.LocationHelper;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;
import com.unitesk.requality.tool.message.RMessages;

public abstract class AbstractResTracker implements IResTracker, ICacheStorageProvider {
  protected CacheStorage gcss = null;
  protected Map<String,CacheStorage> caches = null;
  protected Path cpPath = null;
  protected boolean noGUI = false, alwaysOK = false;
  private ConcurrentHashMap<String, ProjectLoadingState>projectLoadingState = new ConcurrentHashMap<>();
  protected ConsoleProgressMonitor monitor = new ConsoleProgressMonitor();
  protected ReentrantReadWriteLock projectsLock = new ReentrantReadWriteLock();//lock for project loading
  protected boolean inited = false;//Change to true when tracker is initially inited
  protected abstract OperationResult sourceSpecificTrackTreeDB(Object obj, String lname, String pname, boolean ignoreVersion, boolean noParallel, boolean check, boolean initCache, TreeDB db);
  private final ReentrantLock cachesLock = new ReentrantLock();
  private final ReentrantLock tcmlock = new ReentrantLock();
  private final ReentrantLock  listenersLock = new ReentrantLock();
  public CacheStorage getCacheStorage() throws Exception{
    if(gcss == null)
    {
      cachesLock.lock();
      try {
        if(gcss == null)
          initCaches();
      }finally {
        cachesLock.unlock();
      }
    }
    return gcss;
  }
  private static TreeState[]checkerChangeStates = new TreeState[] {TreeState.HALTED, TreeState.RESTART};
  public AbstractResTracker(){
    addProjectLoadingStateHook();
    TreeStateManager tsm = getTreeStateManager();
    if(tsm == null)
      return;
    tsm.addStateHook("*", haltedHookStates, trackerReadyHaltedHookHandler);
    tsm.addStateHook("*", projectsLoadingHookStates, stateToNotifyEventsHanlder);
    tsm.addStateHook("*", projectRestartHookStates, projectRestartHookHanlder);
    tsm.addStateHook("*", projectStateChangesHookStates, projectStateChangesHookHanlder);
    
    tsm.addStateHook("*", projectsLoadingHookStates, new AAllTreeStateChangeHandler() {
        @Override
        public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
           TreeState oldState, TreeState state) {
         if(db==null) {
           RLogger.logError("TODO test");;
           return;
         }
         URI loc = LocationHelper.getLocation(db, true);
         if(loc == null && state == TreeState.READY )
           RLogger.logError("Null location for handled event(checkers hook)");
         if(state == TreeState.READY && !db.isBaseDisposed() &&
             (loc!=null && (getProjectLoadingState(loc, db.getSemiProjectName())==ProjectLoadingState.LOADED))) {
           if(IResTracker.storedVersionCorrect(loc)) 
             Global.runCheckers(db, true, false);
         }
         //TODO vers update?
       }
    });
    tsm.addStateHook("*", checkerChangeStates, new AAllTreeStateChangeHandler() {
      @Override
      public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState,
         TreeState state) {
        CheckerContainer cc = db.getMetaObject(CheckerContainer.META_CONTAINER_NAME);
        if(cc!=null)
          cc.stopOldThread();
      }
    });
  }
  private static TreeState[]haltedHookStates = new TreeState[] {TreeState.HALTED, TreeState.CANCELED, TreeState.DISPOSED};
  private static TreeState[]projectsLoadingHookStates = new TreeState[] {TreeState.READY, TreeState.HALTED};
  private static TreeState[]projectStateChangesHookStates = new TreeState[] {TreeState.LOADED};
  private static TreeState[]projectRestartHookStates = new TreeState[] {TreeState.HALTED,TreeState.RESTART, TreeState.READY};
  
  private IStateChangeHandler projectStateChangesHookHanlder = new AAllTreeStateChangeHandler() {
    @Override
    public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
        TreeState oldState, TreeState state) {
      if(state == TreeState.LOADED) {
        tsm.setState(lname, pname, db, ri, TreeState.READY, true);
      }
    }
  };
  private IStateChangeHandler stateToNotifyEventsHanlder = new AAllTreeStateChangeHandler() {
    @Override
    public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState, TreeState state) {
     // TreeStateManager.getOne(AbstractResTracker.this).removeStateHook(lname, states, this);
      if(db == null) {
        db = ResManager.getManager().getTreeDB(null, lname, pname, true, monitor);//HOW?
        //RLogger.logInfo("State handler experiences null project reference "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
      }
      switch(state) {
        case HALTED:{
          notifyListeners(Moment.BEFORE, lname, pname, db, TreeEvent.TREE_CLOSE);//TODO move?
          notifyListeners(Moment.AFTER, lname, pname, db, TreeEvent.TREE_CLOSE);
//          tn.treeClosed(db);   
        }break;
        case READY:{
          if(oldState!=TreeState.MODIFYING) {//transactions
            notifyListeners(Moment.BEFORE, lname, pname, db, TreeEvent.TREE_OPEN);//TODO move?
            notifyListeners(Moment.AFTER, lname, pname, db, TreeEvent.TREE_OPEN);
          }
//          tn.treeOpenned(db);
        }break;
      }
    }
  };

  private IStateChangeHandler trackerReadyHaltedHookHandler = new AAllTreeStateChangeHandler() {
  
    @Override
    public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState, TreeState state) {
      File cd = null;
      if(state == TreeState.CANCELED || state == TreeState.DISPOSED) {
        if(db!=null) {
          db.getTracker().getTreeStateManager().setState(lname, pname, db, TreeState.HALTED);
        }
      }else
      if(state == TreeState.HALTED){
        if(db!=null) {
          Path cdp = CoreUtils.getCacheDir(db);
          if(cdp!=null)
            cd = cdp.toFile();
          if(cd!=null)
            CacheStorage.closeRecConnection(cd);
          db.getCacheStorage().close();
          if(db.getMonitor().isDBLocal())
            db.getMonitor().cancel();
        }
      }
    }
  };
  private static String META_RESTART_TIMEOUT = "PROJETCT_RESTART_HOOK_TIMEOUT";
  private IStateChangeHandler projectRestartHookHanlder = new AAllTreeStateChangeHandler() {
    private static int TIMEOUT_CONST = 300000;
    //Map<TreeDB, Long> restartTimeoutMap = new HashMap<>();
    URI loc = null;
    @Override
    public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
        TreeState oldState, TreeState state) {
      if(loc == null)
        loc = LocationHelper.getLocation(db, true);//TODO check (especially on rename)
      Long restartTimeout = db.getMetaObject(META_RESTART_TIMEOUT);
      if(restartTimeout == null && state == TreeState.RESTART) {
        restartTimeout = System.currentTimeMillis()+TIMEOUT_CONST;
        synchronized(projectRestartHookHanlder) {
          db.registerMetaObject(META_RESTART_TIMEOUT, restartTimeout);
        }
      }
      if(state == TreeState.RESTART) {
        if(restartTimeout != null && System.currentTimeMillis()>restartTimeout)  {
          state = TreeState.READY;
          tsm.setState(lname, pname, db, ri, TreeState.READY, true);//TODO do smth?
        }
      }
      if(state == TreeState.READY) {
        synchronized(projectRestartHookHanlder) {
          db.registerMetaObject(META_RESTART_TIMEOUT, null);
        }
        if(loc!=null) {
  //            if(nhadded && tracker.isNewHandling(loc, pname))
  //              tracker.removeNewHandling(loc, pname);
        }
        else
          RLogger.logDebug("Null location for handled event (Restart timeout handling on '"+lname+"'");
        //tsm.setState(lname, ri, TreeState.READY);//TODO do smth?
      }
    }
  };
  
  public void setPath(Path p){
    this.cpPath = p;
  }
  protected TrackerCacheManipulatorsManager tcm;
  public TrackerCacheManipulatorsManager getCMM(TreeDB dbfrom){
    if(tcm == null){
      tcmlock.lock();
      try {
        if(tcm == null) {
          tcm = new TrackerCacheManipulatorsManager();
          tcm.initCM(this, SettingsManipulator.init(this), monitor );
        }
      }finally {
        tcmlock.unlock();
      }
    }
    return tcm;
  }
  public void initCaches() throws Exception{
    caches = new HashMap<>();
    Set<String> dbnames = CacheStorage.getCacheDBNames(false);
    for(String s: dbnames)
      if(s.equals(IAPICacheProvider.DEFAULT))
        gcss = new CacheStorage(s, false, cpPath,"crossproject","", null, null, null);
      else
        caches.put(s, new CacheStorage(s, false, cpPath,"crossproject_"+s,"", null, null, null));
  }

  public CacheStorage getCacheStorage(String id){
    if(caches == null)
      throw new RuntimeException("Caches are not inited!");
    return caches.get(id);
  }
  @Override
  public CacheStorage getCacheStorage(TreeDB dbfrom){
    if(dbfrom!=null)
      return dbfrom.getCacheStorage();
    return null;//TODO?
    //return getCacheStorage();
  }
  @Override
  public void clearCache(TreeDB db, String localName, boolean delete){
    if(isTracking(db)){
      TrackerCacheManipulatorsManager cmm = getCMM(db);
      try {
        cmm.clearCaches(db, cmm.getSettingsStore());
        db.clearCaches(delete, false);
      }finally {
        //cmm.clearCMMCaches();
      }
    }else
      return;
    //TODO mb add delete?
  }
  
  public void clearNodeCache(TreeDB db, TreeNode node, boolean delete){
    db.clearNodeCache(node.getUUId(), node.getWorkParentUUID(), delete);
  }
  public boolean isTracking(TreeDB db){
    String s = getLocation(db, true);//noCache tends to be quite better
    return s!=null;
  }
  public OperationResult updateVersions(File f, AtomicReference<TreeDB> dba, RevisionInfo ri, boolean ignoreVers, IProgressMonitor mon) {
    byte l = 1;
    String lname = null;
    boolean notUpdating = true;
    try {
      if(f == null) {
        notUpdating = false;
        return OperationResult.UNSUCEEDED;
      }
      lname = f.getName();
      URI uri = f.toURI();
      String pname = CoreUtils.getProjectName(f.toPath().toFile());
      if (!isOpen(uri)) {
        RLogger.logInfo("project is not opened");
        notUpdating = false;
        return OperationResult.UNSUCEEDED;
      }
      
      File fc = f.toPath().resolve(CoreUtils.CACHE_FOLDER_NAME).toFile();
      if(!fc.exists())
          fc.mkdirs();
      
      if (ignoreVers) 
        return OperationResult.LOAD_AS_IT;
      ProjectLoadingState state = setProjectLoadingState(uri, pname, ProjectLoadingState.VERSION_UPDATE);
      boolean up = true;
      try {
        TreeStateManager tsm = TreeStateManager.getOne(this);
        final List<VersionUpdateInfo> vilist = StoredFormatVersioning.getOne().listProjectUpdates(fc.getParentFile());
        if(!vilist.isEmpty()){
          notUpdating = false;
          if(tsm.getState(lname, ri)!=TreeState.RESTART)
            tsm.setState(lname, pname, null, ri, TreeState.NOT_LOADED, true);
          StringBuilder updates = new StringBuilder("");
          StringBuilder plugs = new StringBuilder("");
          StringBuilder problems = new StringBuilder("");
          int pl = 0;
          for (VersionUpdateInfo vi : vilist) {
            if (vi.getConverters() == null) {
              pl++;
              plugs.append("\n");
              plugs.append(vi.getGroupId());
              problems.append("\n");
              problems.append(vi.getStatus());
            } else {
              updates.append("\n");
              updates.append(vi.toString());
            }
          }
          if (pl > 0) {
            if(problems.length() != 0)
              if(!noGUI)
               RMessages.openError(
                "Project Loading",
                "Problems appeare while updating '"
                    + lname
                    + "' project. "+problems,"",false);
              else
               RLogger.logError("Cannot update project "+f+" "+problems); 
//            setProjectLoadingState(uri, pname, ProjectLoadingState.NOT_LOADING);
            close(uri);
            notUpdating = false;//tends to be in disposed state in this case
            return OperationResult.UNSUCEEDED;
          }
          final String flname = lname;
          final String fpname = pname;
            Runnable okAction = new Runnable() {
              @Override
              public void run() {
                TreeStateManager tsm = TreeStateManager.getOne(AbstractResTracker.this);
                tsm.setState(flname, fpname, dba.get(), ri, TreeState.RESTART, true);
                try {
                  if(isOpen(uri)) {
                    tsm.setDisposed(flname, fpname, dba.get(), ri);//TODO test
                    close(uri);
                  }
                  
                  setProjectLoadingState(f.toURI(), pname, ProjectLoadingState.VERSION_UPDATE);
                  ConversionResult err = StoredFormatVersioning.getOne().update(AbstractResTracker.this, f, ri, vilist, mon);
                  if (err==null || (err.error!=null && err.error.length() > 0)){
                    String msg = err == null? "error null":err.error.toString();
                    if(noGUI)
                      RLogger.logError(msg);
                    else
                      RMessages.openBigInfo("Project Loading","loading error!"+msg, false);
                  }else{
                    String res = err.result;
                    String msg = "Conversion of '"
                        + flname
                        + "' to new format was completed."+(
                        (res==null||res.isEmpty())?"":"\nINFO:\n"
                        + res); 
                    if(!noGUI)
                      RMessages.openInfo(
                        "Project Loading", msg, false
                        );
                    else
                      RLogger.logInfo( msg ); 
                  }
                  if (err!=null && err.error.length() == 0) {
                    open(uri);//will it be tracked?
                    TreeDB db = loadTreeDBDescription(f, ri, flname, mon);
                    if(db!=null) {
                      dba.set(db);
                      db.revision = ri;//TODO look into
                      db.getTracker().getTreeStateManager().setRevisionInfo(db, ri);
                      tsm.setState(flname, fpname, db, ri, TreeState.RESTART, true);//if we still in restart then it is an error
//                      OperationResult tlr = trackTreeDB(f.toURI(), flname,CoreUtils.getProjectName(f), false,true, true, true, db);
//                      if(tlr == OperationResult.UNSUCEEDED) {
//                        //TODO problems if moved\renamed
////                        if(isNewHandling(uri, pname)) //TODO correct?
////                          removeNewHandling(uri, flname, pname);
//                        setProjectLoadingState(uri, pname, ProjectLoadingState.NOT_LOADING);
//                        return;
//                      }
//                      if(tlr == OperationResult.PARALLEL) {
//                        RLogger.logError("tree loading resulted parallel after version update");
//                        return;
//                      }
                    }
                   // tsm.setState(f.getName(), ri, TreeState.READY_NO_CACHE);
                    //trackTreeDB(f,f.getName(),null, false, db);
                  }else{
                    if(!isOpen(uri))
                      open(uri);
                    tsm.setDisposed(flname,fpname, dba.get(), ri);//TODO test
                    markNonLoadable(uri);
                    setProjectLoadingState(uri, pname, ProjectLoadingState.NOT_LOADING);
                  }
                } catch (Exception e) {
                  RLogger.logError(e);
                  if(!isOpen(uri))
                    open(uri);
                  tsm.setDisposed(flname,fpname, dba.get(), ri);
                  markNonLoadable(uri);
                } finally {
//                  if(tsm.getState(flname, ri) == TreeState.RESTART)
//                  {
//                    setProjectLoadingState(uri, pname, ProjectLoadingState.NOT_LOADING);
//                    tsm.setState(flname, fpname, dba.get(), ri, TreeState.CANCELED, true);//if we still in restart then it is an error
////                  if(isInNewHandling(uri)) {
////                    removeNewHandling(uri);
////                  }
//                  }
                }
              }
            };
            Runnable cancelAction = new Runnable() {
              public void run() {
                try {
                  if(isOpen(uri))
                    close(uri);
                }finally {
                  tsm.setState(flname,fpname, null, ri, TreeState.CANCELED, true);
                  setProjectLoadingState(uri, pname, ProjectLoadingState.NOT_LOADING);
                }
              }
            };
            up = false;//mark must be called on cancel\ok actions
            if(noGUI) {
              if(alwaysOK) {
                okAction.run();
                return OperationResult.SUCEEDED;
                //return true;
              }else {
                cancelAction.run();
                return OperationResult.UNSUCEEDED;
              }
            }else{
              Pair<AtomicBoolean, OperationResult> ret = RMessages.openQuestionOkCancel(
                "Project Loading",
                "Project '"
                    + f.getName()
                    + "' format is out of date. Installed Requality version can't process it. Do you want to update the project to proper format?",
                false,
                true,//needs to wait for it
                okAction,
                cancelAction);
              while(!ret.getFirst().get()) {//wait untill answer job is over
                try {
                  Thread.currentThread().join(500);
                } catch (InterruptedException e) {
                }
              }
              return ret.getSecond();
            }
        }else
          setProjectLoadingState(uri, pname, state);
      }finally {
      }
    }finally {
    }
    return OperationResult.LOAD_AS_IT;//no version updates
  }

  @Override
  public ProjectLoadingState getProjectLoadingState(URI uri, String pname) {
    if(uri == null)
      return ProjectLoadingState.NOT_LOADING;
    if(pname == null)
      RLogger.logDebug("null name for url "+uri);
    synchronized (projectLoadingState) {
      String key = getUrlString(uri, pname);
      return projectLoadingState.getOrDefault(key, ProjectLoadingState.NOT_LOADING);
    } 
  }

  public ProjectLoadingState getProjectLoadingState(URI uri) {
    if(uri == null)
      return ProjectLoadingState.NOT_LOADING;
    String pname = CoreUtils.getProjectName(new File(uri)); 
    return getProjectLoadingState(uri, pname); 
  }
  public static String getUrlString(URI oldURI, String pname) {
    String us = oldURI.toString();
    if(pname == null)
      RLogger.logDebug("null name for url "+us);
    if(us.endsWith("/"))
      us = us.substring(0, us.length()-1);
    return us+"|"+(pname!=null?pname:"null");
  }

  public void addProjectLoadingStateHook() {
    AtomicBoolean isHookRemoved = new AtomicBoolean(false);
    final TreeStateManager tsm = getTreeStateManager();
    if(tsm == null)
      return;
    IResTracker tracker = this;
    IStateChangeHandler hook = new AAllTreeStateChangeHandler() {
      @Override
      public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
          TreeState oldState, TreeState state) {
        if(db == null) {
          return;
        }
        URI uri = LocationHelper.getLocation(db, false);//location, including cached one
        if(uri == null)
          RLogger.logError("Attempt to fire events on project without cached location");
        String fpname =pname;
        if(state == TreeState.READY) {
          if(IResTracker.storedVersionCorrect(uri)) {
            setProjectLoadingState(uri, fpname,
              state == TreeState.READY? ProjectLoadingState.LOADED:ProjectLoadingState.NOT_LOADING);
           }else
            RLogger.logDebug("Project "+fpname+" loading was initiated but hook found that loading is still not over");
//          tracker.setProjectLoadingState(uri, fpname, ProjectLoadingState.LOADED);
        }
      }
    };
    tsm.addStateHook("*", projectsLoadingHookStates, hook);
  }
  @Override
  public ProjectLoadingState setProjectLoadingState(URI uri, String pname, ProjectLoadingState state) {
    if(uri == null)
      return ProjectLoadingState.NOT_LOADING;
    //System.err.println(pname+" "+state+" "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
    if(pname == null)
      RLogger.logDebug("null name for url "+uri);
    synchronized (projectLoadingState) {
      String key = getUrlString(uri, pname);
      if(state == ProjectLoadingState.NOT_LOADING || state == null)
        return projectLoadingState.remove(key);
      else
        return projectLoadingState.put(key, state);
    }   
    
  }
//  private OperationResult updateVersion(TreeDB db, File f, RevisionInfo ri, boolean ignoreVers, IProgressMonitor mon) {
//    boolean notUpdating = false;
//    String lname = null, pname = null;
//    try {
//      URI uri = f.toURI();
//      lname = db.getLocalName();
//      pname = db.getProjectName();//TODO possible?
//      if(ignoreVers)
//        return OperationResult.SUCEEDED;
//      if(f == null || !f.exists())
//        throw new RuntimeException("Cannot load versions for null project");
//      final List<VersionUpdateInfo> vilist = StoredFormatVersioning.getOne().listProjectUpdates(f);
//      if(!vilist.isEmpty()){
//        StringBuilder updates = new StringBuilder("");
//        StringBuilder plugs = new StringBuilder("");
//        StringBuilder problems = new StringBuilder("");
//        int pl = 0;
//        for (VersionUpdateInfo vi : vilist) {
//          if (vi.getConverters() == null) {
//            pl++;
//            plugs.append("\n");
//            plugs.append(vi.getGroupId());
//            problems.append("\n");
//            problems.append(vi.getStatus());
//          } else {
//            updates.append("\n");
//            updates.append(vi.toString());
//          }
//        }
//        if (!ignoreVers){
//          if (pl > 0) {
//            if(problems.length() != 0)
//              if(!noGUI)
//                RMessages.openError(
//                  "Project Loading",
//                  "Problems appeare while updating '"
//                    + lname
//                    + "' project. "+problems,"", false);
//              else
//                RLogger.logError("Project loading failure! on "+f.toURI());
//            setProjectLoadingState(uri, pname, ProjectLoadingState.NOT_LOADING);
//            close(f.toURI());
//            TreeStateManager.getOne(this).setState(lname, db, ri, TreeState.HALTED, true);
//            return OperationResult.UNSUCEEDED;
//          }
//          if(updates.length() > 0) {
//            TreeStateManager.getOne(this).setState(lname, db, ri, TreeState.NOT_LOADED, true);//TODO think
//            try {
//              close(f.toURI());
//            } catch (Exception e) {
//              RLogger.logError(e);
//            }
//            final String flname = lname;
//            final String fpname = pname;
//            Runnable okAction = new Runnable() {
//              @Override
//              public void run() {
//                boolean errorState = false;
//                try {
//                  try {
//                    if(isOpen(f.toURI()))
//                      close(f.toURI());
//                    ConversionResult err = StoredFormatVersioning.getOne().update(AbstractResTracker.this, f, ri, vilist, mon);
//                    if (err.error.length() > 0){
//                      String msg = err.error.toString();
//                      if(noGUI)
//                        RLogger.logError(msg);
//                      else
//                        RMessages.openError("Project Loading","loading error!", msg, false);
//                      
//                      TreeStateManager.getOne(AbstractResTracker.this).setState(flname, db, ri, TreeState.HALTED, true);
//                    }else{
//                      String res = err.result;
//                      String msg = "Conversion of '"
//                          + flname
//                          + "' to new format was completed."+(
//                          (res==null||res.isEmpty())?"":"\nINFO:\n"
//                          + res);
//                      if(!noGUI)
//                        RMessages.openInfo(
//                          "Project Loading", msg, false
//                          );
//                      else
//                        RLogger.logInfo( msg ); 
//                    }
//                    if (err.error.length() == 0) {
//                      open(f.toURI());
//                      handleNewProject(f.toURI(), flname, false, false);
//                      //(f.toURI(), ri, f.getName(), true)
//                      //trackProject(project, ri, true);
//                    }else{
//                      errorState = true;
//                    }
//                  } catch (Exception e) {
//                    RLogger.logError(e);
//                    errorState = true;
//                  }finally{
//                  }
//                }finally {
//                  if(errorState)
//                  {
//                    TreeStateManager.getOne(AbstractResTracker.this).setState(flname, db, ri, TreeState.CANCELED, true);
//                    if(isOpen(f.toURI()))
//                      open(f.toURI());
//                    markNonLoadable(f.toURI());
//                  }
//                }
//              }
//            },
//            cancelAction = new Runnable() {
//              public void run() {
//                setProjectLoadingState(uri, fpname, ProjectLoadingState.NOT_LOADING);
//                if(isOpen(f.toURI()))
//                  close(f.toURI());
//                TreeStateManager.getOne(AbstractResTracker.this).setState(flname,db, ri, TreeState.CANCELED, true);
//              }
//            };
//            if(!noGUI) {
//              if(alwaysOK) {
//                okAction.run();
//                return OperationResult.SUCEEDED;
//              }else {
//                cancelAction.run();
//                return OperationResult.UNSUCEEDED;
//              }
//            }
//            else{
//              Pair<AtomicBoolean, OperationResult> ret = RMessages.openQuestionOkCancel(
//                "Project Loading",
//                "Project '"
//                    + lname
//                    + "' format is out of date. Installed Requality version can't process it. Do you want to update the project to proper format?",
//                true,
//                false,
//                okAction,
//                cancelAction);
//              while(!ret.getFirst().get()) {
//                try {
//                  Thread.currentThread().join(500);
//                } catch (InterruptedException e) {
//                }
//              }
//              return ret.getSecond();
//            }
//          }else 
//            notUpdating = true;
//        }else 
//          notUpdating = true;
//      }else
//        notUpdating = true;
//    }finally {
//    }
//    return OperationResult.SUCEEDED;//TODO test
//  
//  }
  public final TreeDB getTreeDB(TreeDB dbFrom, String lname, String pname, boolean load, IProgressMonitor mon){
    return getTreeDB(dbFrom, lname, pname, load, true, mon);
  }

  public abstract void markNonLoadable(URI uri);
  
  public void fireDBSettingsChange(Moment m, TreeDB db) {
    tn.notifyListeners(m, db.getLocalName(),db.getProjectName(), db, TreeEvent.SETTS_CHANGED);
  }
  
  private TrackerNotifier tn = new TrackerNotifier(){

    @Override
    protected void treeClosed(TreeDB db){
      //TreeStateManager tsm = TreeStateManager.getOne(AbstractResTracker.this);
      {
        Global.clearCachesWithEvent(db, null, false, false);
      }
    }
    @Override
    protected void treeOpenned(TreeDB db){
    }
    @Override
    protected void treeDeleted(TreeDB db){
      TreeStateManager tsm = TreeStateManager.getOne(AbstractResTracker.this);
      {
//        JobManager.jm.startBaseJob("clear caches for "+db.getLocalName(), new RunnableWithMonitor() {
//          @Override
//          public void run(IProgressMonitor pm) {
        Global.clearCachesWithEvent(db, null, true, false);

//          }
//        }, true);
      }
    }
  };
  public boolean addListener(Moment m, ITreeStateListener l) {
    return tn.addListener(m, l);
  }

  public boolean removeListener(Moment m, ITreeStateListener l) {
    return tn.removeListener(m, l);
  }
  public void notifyListeners(Moment m, String lname, String pname, TreeDB db, TreeEvent type) {
    listenersLock.lock();
    try {
      tn.notifyListeners(m, lname, pname, db, type);
    }finally {
      listenersLock.unlock();
    }
  }
  
//  public void addStateHook(TreeDB db) {
//    String lname = db.getLocalName();
//    if(lname == null) {
//      RLogger.logError("Tries to add hook to non-tracable db");
//    }
//    else
//      TreeStateManager.getOne(this).addStateHook(lname, states, stateChanger);
//  }
  protected TreeStateManager tsm;
  @Override
  public TreeStateManager getTreeStateManager() {
    if(tsm == null)
      tsm = TreeStateManager.initOne(this);
    return tsm;
  }
  public boolean isInited() {
    return inited;
  }

  public void setInited(boolean b) {
    inited = b;
  }
//  public void addsHaltTrackerReaction() {
//    Path furi = getRootLocation();
//    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
//      @Override
//      public void run() {
//        Path uri = furi;
//        if(uri == null)
//          uri = getRootLocation();
//        if(uri!=null)
//      try {
//      getCacheStorage()
//          .closeRecConnection(
//             uri.toFile()
//          );
//      } catch (Exception e) {
//      // TODO Auto-generated catch block
//       e.printStackTrace();
//      }
//      }
//    }));
//  }
  public abstract void bindTreeToProject(Object proj, TreeDB db, RevisionInfo ri, String lname, File ch);
  public abstract void registerNames(TreeDB db, String lname, String pname);
  
  @Override
  public ReentrantReadWriteLock getProjectLoadingLock() {
    return projectsLock;
  }
  public static void addUndoRedoHook(TreeDB db) {
    db.addTransactionListener(
        new ITransactionListener() {
          @Override
          public void _dispose() {
            //IOperationHistory history = PlatformUI.getWorkbench()
            //        .getOperationSupport().getOperationHistory();
            //history.dispose(getContext(), true, true, true);
          }

          @Override
          public void execute(TransactionStorage tStorage) {
            UndoRedoMech.registerOperation(tStorage);
          }

          @Override
          public void redo(TransactionStorage tStorage) {}

          @Override
          public void undo(TransactionStorage tStorage) {
            // TODO Auto-generated method stub

          }
        });
  }
  private OperationResult versionUpdatesCheck(Object p, AtomicReference<TreeDB> db, RevisionInfo ri, boolean ignoreVersion) {
    //com.unitesk.requality.core.operation.IProgressMonitor mon = db.get().getMonitor();// TODO IDE monitor
    String uloc = getLocation(p);
    if(uloc == null)
      return OperationResult.UNSUCEEDED;
    if(uloc.startsWith("memory://"))
      return OperationResult.SUCEEDED;//TODO check?
    Path loc = new File(URI.create(uloc)).toPath();//TODO remade?
    IProgressMonitor mon = db.get().getMonitor().isCanceled()?new ConsoleProgressMonitor():db.get().getMonitor();
    OperationResult updateRes = ignoreVersion ? OperationResult.LOAD_AS_IT
        : updateVersions(loc.toFile(), db, ri, false, mon);
    if (updateRes == OperationResult.SUCEEDED || updateRes == OperationResult.LOAD_AS_IT) {
      return OperationResult.SUCEEDED;
    } else if (updateRes == OperationResult.UNSUCEEDED) {
//          if(wasNotLoading)
//            hook.stateChanges(db, lname, ri, TreeState.READY, TreeState.READY);
    }
    return OperationResult.UNSUCEEDED;
  }
  @Override
  public OperationResult trackTreeDB(Object obj, String lname, String pname, boolean ignoreVersion, boolean noParallel, boolean check, boolean initCache, TreeDB dbb) {
    AtomicReference<TreeDB> db = new AtomicReference<TreeDB> (dbb);
    URI loc = db.get().getStorage().getLocation();
    if(loc == null)
      return OperationResult.UNSUCEEDED;//no location for tree db, cannot load it 
    db.get().cachedLocation = loc.toString();
    CacheStorage cs = db.get().getCacheStorage();
    CacheTransactionDescription st = cs.startCacheTransaction(pname, CacheStorage.COMMIT_ALL);
    try {
      OperationResult or = versionUpdatesCheck(obj, db, null, false);
      if(ignoreVersion || or == OperationResult.SUCEEDED) {
        int it = 0;
        while(db.get().isDisposed()&&db.get()==dbb && it<20)
        {
          it++;
          try {
            Thread.currentThread().join(1000);
          } catch (InterruptedException e) {
          }
        }
        try {//TODO here names are not inited. May it cause problems?
          IProgressMonitor mon = db.get().getMonitor().isCanceled()?new ConsoleProgressMonitor():db.get().getMonitor();
          db.get().getStorage().initCaches(db.get().getStorage(), CacheInitializationStage.BEFORE_TRACK, mon);
        } catch (Exception e) {
          e.printStackTrace();
        }
        or = sourceSpecificTrackTreeDB(obj, lname, pname, ignoreVersion, noParallel, check, initCache, db.get());
        if(or == OperationResult.SUCEEDED) {
          addUndoRedoHook(db.get());
          notifyListeners(Moment.BEFORE, lname, pname, db.get(), TreeEvent.TREE_OPEN);//Update state on
        notifyListeners(Moment.AFTER, lname, pname, db.get(), TreeEvent.TREE_OPEN);//Update state on
        }
        if(or == OperationResult.UNSUCEEDED) {
          CacheStorage.closeRecConnection(new File(loc));
        }
        return or;
      }
      return or;
    }finally {
      if(st!=null && st.error == null)
        cs.commit(st);
    }
  }
}