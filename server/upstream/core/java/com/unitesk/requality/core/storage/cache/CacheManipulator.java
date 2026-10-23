package com.unitesk.requality.core.storage.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.CacheInitializationStage;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.repo.RevisionInfo;

public abstract class CacheManipulator<T> {
  private static ConcurrentHashMap<String, ConcurrentHashMap<String, CacheManipulator>> parallelHanlings = new ConcurrentHashMap<>();
  private ConcurrentLinkedQueue<String> settNamesOfParallel = new ConcurrentLinkedQueue<>();
  private Thread processUpdating = null;
  public static String getCPTreeDBId(String cachename, TreeDB db){
    //Local name seems to be more proper for cache init
    return cachename+db.getLocalName()+TreeStateManager.getOne(db.getTracker()).getRevisionOrHeadString(db);
  }
  public static String getTreeDBId(String cachename, TreeDB db){
    return cachename+db.getLocalName()+TreeStateManager.getOne(db.getTracker()).getRevisionOrHeadString(db);
  }
  protected IProgressMonitor parallelMon = null;
  protected abstract String[] getSettingsNames(T db);
  protected abstract String[] getVersions();
  protected abstract boolean isCrossProjectCache();
  protected abstract OperationResult innerDropCache(T db, SettingsManipulator cman);
  protected abstract OperationResult innerInitFirst(T db, SettingsManipulator cman, IProgressMonitor mon);
  protected abstract OperationResult innerInitNotFirst(T db, SettingsManipulator cman, IProgressMonitor mon);
  private static String IN_PROGRESS_CACHE = "in_progress";
  private static String PARALLEL_CACHE = "parallel";
  //protected abstract void setCacheInited(SettingsManipulator cman, T db, boolean state);
  public final OperationResult clearCache(T db, SettingsManipulator cman){
    String dbString = "Clear cache for "+(db!=null?db.toString():"NULL")+" "+getName();
    RLogger.logInfo(dbString+" secondary init");
    return innerDropCache(db, cman);
  }
  public final OperationResult initFilledCache(T db, CacheInitializationStage stage, SettingsManipulator cman, IProgressMonitor mon){
    String dbString = "Init filled cache for "+db.toString()+" "+getName();
    RLogger.logDebug("init filled cache "+cman.toString()+" "+getName());
    OperationResult ret = OperationResult.UNSUCEEDED;
    mon.beginTask(dbString, 1);
    try {
      RLogger.logInfo(dbString+" secondary init");
      ret = innerInitNotFirst(db, cman, mon);
      RLogger.logInfo(dbString+" inited");
    }finally {
      mon.doneTask();
    }
    return ret;
  }
  public final void initNotFilledCache(T db, CacheInitializationStage stage, SettingsManipulator cman, IProgressMonitor mon){
    String dbString = "Init not inited cache for "+(db!=null?db.toString():"NULL")+" "+getName();
    mon.beginTask(dbString, 1);
    try {
      RLogger.logInfo(dbString+" prepare drop");
      innerDropCache(db, cman);//clear old records for db
      RLogger.logInfo(dbString+" dropped, init cache");
      setCacheInited(cman, db, CacheManipulatorInitializationStage.IN_PROGRESS);
      synchronized (this) {
        if(processUpdating == null) {
          processUpdating = Thread.currentThread();
        }else
          return;//parallel run
	  }
      OperationResult inited = innerInitFirst(db, cman, mon);
      CacheManipulatorInitializationStage stateChange = null;
      switch (inited) {
        case PARALLEL:
          stateChange = CacheManipulatorInitializationStage.PARALLEL; 
          break;
        case LOAD_AS_IT:
        case SUCEEDED:
          stateChange = CacheManipulatorInitializationStage.READY; 
          break;
        case UNSUCEEDED:
          stateChange = CacheManipulatorInitializationStage.NOT_INITED; 
          break;
      }
      if(stateChange!=null)
        setCacheInited(cman, db, stateChange);
      switch(inited) {
        case UNSUCEEDED:
          clearCache(db, cman); 
          break; 
        case PARALLEL:
          synchronized (this){
            if(!registerParallelInitiation(cman, db, mon))
              processUpdating = null;
          }
          break;
        default:
          break;
      }
      if(stateChange!=CacheManipulatorInitializationStage.PARALLEL)
        synchronized (this) {
          processUpdating = null;
        }
      RLogger.logInfo(dbString+" cache initiated, result - "+stateChange);
    } catch(Exception e) {
      RLogger.logError(e);
      synchronized (this) {
        processUpdating = null;
      }
    }
    finally {
      mon.doneTask();
    }
  }
  private synchronized boolean registerParallelInitiation(SettingsManipulator cman, T db, IProgressMonitor mon) {
    synchronized(this){
      if(parallelMon!=null)
        return false;
      String[] names = getSettingsNames(db);
      if(getCacheInitationState(cman, db) == CacheManipulatorInitializationStage.PARALLEL)
        for(int i = 0;i<names.length;i++) 
          settNamesOfParallel.add(names[i]);
      parallelMon = mon;
    }
    String path = cman.getLocation();
    synchronized (parallelHanlings) {
      if(!parallelHanlings.containsKey(path)) 
        parallelHanlings.put(path, new ConcurrentHashMap<>());
      CacheManipulator cm = parallelHanlings.get(path).put(getName(),this);
      if(cm!=null)
        cm.stopParallelProcessing();
    }
    return true;
  }

  public static void dropOldParallelConnections(SettingsManipulator sman, CacheManipulator cman) {
    if(cman == null || sman == null)
      return;
    String path = sman.getLocation();
    synchronized (parallelHanlings) {
      if(!parallelHanlings.containsKey(path))
        return;
      CacheManipulator cm = parallelHanlings.get(path).remove(cman.getName());
      if(cm!=null)
        cm.stopParallelProcessing();
	}
  }
  public boolean inParallelState(T db, CacheInitializationStage stage, SettingsManipulator sman,
    IProgressMonitor mon) {
    String[] names = getSettingsNames(db);
    boolean ret = false;
    for(int i = 0;i<names.length;i++)
      if(settNamesOfParallel.contains(names[i]))
        ret = true;
    return ret;
  }
  public synchronized boolean inProcessState(T db, CacheInitializationStage stage, SettingsManipulator sman,
      IProgressMonitor mon) {
    return processUpdating!=null;
  }
  private synchronized void unregisterParallelInitiation(SettingsManipulator cman, T db) {
    String[] names = getSettingsNames(db);
    for(int i = 0;i<names.length;i++) 
      settNamesOfParallel.remove(names[i]);
      
  }
  public synchronized void endParallelInitByState(SettingsManipulator cman, T db, CacheManipulatorInitializationStage b){
    setCacheInited(cman, db, b);
    unregisterParallelInitiation(cman, db);
    synchronized (this) {
      parallelMon = null;
      processUpdating = null;
      
    }
  }
  
  public synchronized void setCacheInited(SettingsManipulator cman, T db, CacheManipulatorInitializationStage b) {
    ReentrantLock lock = CacheManipulatorsManager.getReentrantLock(cman);
    lock.lock();
    String[]defs = getVersions();
    String[] names = getSettingsNames(db);
    try{
      for(int i = 0;i<names.length;i++)
      {
        RLogger.logDebug("set cache init state "+cman.toString()+" "+db.toString()+" "+names[i]+" "+b);
        String writeS = "0";
        switch(b) {
          case IN_PROGRESS:
            writeS = IN_PROGRESS_CACHE; break;
          case PARALLEL:
            writeS = PARALLEL_CACHE; break;
          case READY:
            writeS = defs[i]; break;
        }
        cman.setProjectSetting(names[i], writeS);
      }
    }finally{
      lock.unlock();
    }
  }
  public CacheManipulatorInitializationStage getCacheInitationState(SettingsManipulator cman, T db) {
    ReentrantLock lock = CacheManipulatorsManager.getReentrantLock(cman);
    lock.lock();
    String[] defs = getVersions();
    String[] names = getSettingsNames(db);
    try{
      if(names.length==0)
        return CacheManipulatorInitializationStage.READY;
      CacheManipulatorInitializationStage ret = CacheManipulatorInitializationStage.NOT_INITED;
      for(int i = 0;i<names.length;i++)
      {
        String state = cman.getSetting(names[i], "0");
        if(state.equals(PARALLEL_CACHE))
          return CacheManipulatorInitializationStage.PARALLEL;
        else
          if(state.equals(IN_PROGRESS_CACHE))
            ret = CacheManipulatorInitializationStage.IN_PROGRESS;
          else
            if(state.equals(defs[i])){
              if(ret == CacheManipulatorInitializationStage.NOT_INITED)
                ret = CacheManipulatorInitializationStage.READY;
            }else
              return CacheManipulatorInitializationStage.NOT_INITED; 
      }
      return ret;
    }finally{
      lock.unlock();
    }
  }
  public void clearNodeCache(T db, SettingsManipulator settingsStore, TreeNode node) {
    //TODO
  }
  public void stopParallelProcessing() {
    synchronized (this) {
      if(parallelMon!=null)
        parallelMon.cancel();	
	}
  }
  public abstract void close();
  public abstract String getName();
  public CacheManipulatorInitializationStage getCacheState(SettingsManipulator cman, T treeDB, CacheInitializationStage stage) {
    if(treeDB instanceof TreeDB) {
      TreeDB db = (TreeDB) treeDB;
      RevisionInfo ri = TreeStateManager.getOne(db.getTracker()).getRevisionInfo(db);
      if(ri!=null && isNonRevision()) 
        return CacheManipulatorInitializationStage.NON_APPLIABLE;
    }
    return getCacheInitationState(cman, treeDB);
  }
  protected abstract boolean isNonRevision();
}
