package com.unitesk.requality.core.storage.cache;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.CacheInitializationStage;
import com.unitesk.requality.core.storage.OperationResult;

public abstract class CacheManipulatorsManager<W,T> {
  private static String NO_LOCATION = "no_location_path";
  
  public static enum Status{
    NOT_INITED, INITED, DISPOSED
  }
  public Status initedFlag = Status.NOT_INITED;
  protected List<CacheManipulator<T>> list = new LinkedList<>();
  private List<CacheManipulator<T>> remlist = new LinkedList<>();
  protected Map<String, CacheManipulator<T>> apiMans = new HashMap<>();
  protected static  Map<String, ReentrantLock> stateChangeLocks = new ConcurrentHashMap<>();
  protected SettingsManipulator<T> pscm = null;

  public static ReentrantLock getReentrantLock(SettingsManipulator cman) {
    if(cman == null)
      return getReentrantLock(NO_LOCATION);
    return getReentrantLock(cman.getLocation());
  }
  public static ReentrantLock getReentrantLock(String path) {
    ReentrantLock rl = stateChangeLocks.get(path);
    if(rl == null) {
      rl = new ReentrantLock();
      stateChangeLocks.put(path, rl);
    }
    return rl;
  }
  public void registerManager(CacheManipulator<T> cm){
    synchronized (list) {
      list.add(cm);
  }
  }
  public void registerPersistingManager(CacheManipulator<T> cm){
    synchronized (list) {
      list.add(cm);
      remlist.add(cm);
    }
  }
  
  public CacheManipulatorsManager() {
  }
  public void initCM(W db, IProgressMonitor mon){
    if(initedFlag == Status.INITED)
      return;
    synchronized(list) {
      if(pscm == null) {
        pscm = SettingsManipulator.init(db);
        registerManager(pscm);
      }
    }
    initCM(db, pscm, mon);
    initedFlag = Status.INITED;
  }
  
  protected abstract void initCM(W db, SettingsManipulator sm, IProgressMonitor monitor);
  
  protected List<CacheManipulator<T>> getManipulators(){
    if(initedFlag != Status.INITED)
      throw new RuntimeException("Caсhes manipulator is not inited!");
    return list;
  }

  protected List<CacheManipulator<T>> getPersistedManipulators(){
    if(initedFlag != Status.INITED)
      throw new RuntimeException("Caсhes manipulator is not inited!");
    return remlist;
  }
  public SettingsManipulator getSettingsStore() {
    if(initedFlag != Status.INITED)
      throw new RuntimeException("Caсhes manipulator is not inited!");
    return pscm;
  }
  public void getClose() {
    if(initedFlag != Status.INITED)
      throw new RuntimeException("Caсhes manipulator is not inited!");
    List<CacheManipulator<T>> blist = getManipulators();
    List<CacheManipulator<T>> cm1 = null;
    synchronized (blist) {
      cm1 = new LinkedList<>(blist);
  }
    List<CacheManipulator<T>> fcm1 = cm1;
    blist = getPersistedManipulators();
    synchronized (blist) {
      blist.forEach(one->{if(!fcm1.contains(one))fcm1.add(one);}); 
    }
    for(CacheManipulator<T> cm:fcm1)
      cm.close();
  }
//  public TermsManipulator getTermsManipulator() {
//    return tm;
//  }
//  public LinkResolver getLinkManipulator() {
//    return lm;
//  }
  public void initCache(T treeDB, SettingsManipulator sman, CacheInitializationStage stage, IProgressMonitor mon) {
    if(initedFlag != Status.INITED)
      throw new RuntimeException("Caches manipulator is not inited!");
    List<CacheManipulator<T>> cmb = getManipulators();
    List<CacheManipulator<T>> cm1 = null;
    synchronized(cmb) {
      cm1 = new LinkedList<>(cmb);
    }
    List<CacheManipulator<T>> fcm1 = cm1;
    List<CacheManipulator<T>> pers = getPersistedManipulators(); 
    synchronized(pers) {
      pers.forEach(one->{if(!fcm1.contains(one))fcm1.add(one);});
    }
    
    if(mon.isCanceled())
      return;
    for(CacheManipulator<T> cm:fcm1) {
      try {
        ReentrantLock lock = getReentrantLock(sman.getLocation());
        lock.lock();
        CacheManipulatorInitializationStage cs = null;
        boolean progressState = false;
        boolean parallelState = false;
        try {
          progressState = cm.inProcessState(treeDB, stage, sman, mon);
          parallelState = cm.inParallelState(treeDB, stage, sman, mon);
          cs = cm.getCacheState(sman, treeDB, stage);
        }finally {
          lock.unlock();
        }
        if(cs == CacheManipulatorInitializationStage.NON_APPLIABLE || 
            cs == CacheManipulatorInitializationStage.IN_PROGRESS)
          continue;
        if(cs == CacheManipulatorInitializationStage.READY)
          cm.initFilledCache(treeDB, stage, sman, mon);
        else
          if(cs == CacheManipulatorInitializationStage.NOT_INITED)
            cm.initNotFilledCache(treeDB, stage, sman, mon);
          else
            if(cs == CacheManipulatorInitializationStage.PARALLEL) {
              if(!parallelState)
              {
                CacheManipulator.dropOldParallelConnections(sman, cm);
                cm.initNotFilledCache(treeDB, stage, sman, mon);
              }else
                RLogger.logDebug("Attempt to reinit cache "+ cm.getName()+" in state "+cs); 
            }else
              if(cs == CacheManipulatorInitializationStage.IN_PROGRESS) {
                if(!progressState) {
                  cm.initNotFilledCache(treeDB, stage, sman, mon);
                }else
                  RLogger.logDebug("Attempt to reinit cache "+ cm.getName()+" in state "+cs);
              }else
                RLogger.logDebug("Attempt to init cache "+ cm.getName()+" in state "+cs);
      }catch(Exception e) {
        RLogger.logError(e);
      }
      
    }
  }
  public void clearCaches(T db, SettingsManipulator sman){
    try {
      List<CacheManipulator<T>> list = getManipulators();
      List<CacheManipulator<T>> listi = null;
      synchronized (list) {
        listi = new LinkedList<>(list);
      }//TODO test
      for(CacheManipulator<T> cm:listi) {
        try {
          CacheManipulatorInitializationStage baseState = cm.getCacheState(sman, db, CacheInitializationStage.FULL);
          if(baseState != CacheManipulatorInitializationStage.NOT_INITED && 
              baseState != CacheManipulatorInitializationStage.NON_APPLIABLE && 
              baseState != CacheManipulatorInitializationStage.IN_PROGRESS) {
            cm.setCacheInited(sman, db, CacheManipulatorInitializationStage.IN_PROGRESS);
            try {
              OperationResult or = cm.clearCache(db, sman);
              if(or == OperationResult.UNSUCEEDED)
                cm.setCacheInited(sman, db, baseState);
              else
                if(or == OperationResult.SUCEEDED)
                  cm.setCacheInited(sman, db, CacheManipulatorInitializationStage.NOT_INITED);
                else if (or == OperationResult.PARALLEL)
                  cm.setCacheInited(sman, db, CacheManipulatorInitializationStage.PARALLEL);
            }catch(Exception e) {
              cm.setCacheInited(sman, db, baseState);
              RLogger.logError(e);
            }
          }else
            RLogger.logDebug("Call cache init on incorrect state "+baseState +" on "+db.toString());
        }catch(Exception e) {
          RLogger.logError(e);
        }
      }
    }catch(RuntimeException re) {
      //not inited caches
    }finally {
      if(dropStateAfterClear())
        initedFlag = Status.DISPOSED;
    }
  }
  public abstract boolean dropStateAfterClear();
  public void clearCMMCaches(){
    synchronized(list) {
      pscm = null;
      list.clear();
      remlist.clear();
      initedFlag = Status.DISPOSED;
    }
  }
  
  public void clearNodeCache(T db, SettingsManipulator sman, TreeNode node) {

    List<CacheManipulator<T>> blist = getManipulators();
    List<CacheManipulator<T>> cm1 = null;
    synchronized(blist) {
      cm1 = new LinkedList<>(blist);
    }
    List<CacheManipulator<T>> bcm1 = cm1;
    blist = getPersistedManipulators();
    synchronized(blist) {
      blist.forEach(one->{if(!bcm1.contains(one))bcm1.add(one);});
    }
    
    for(CacheManipulator<T> cm:bcm1)
      cm.clearNodeCache(db, sman, node);
  }
  public void dropParallelCacheInits(Object first, SettingsManipulator cman) {
    List<CacheManipulator<T>> blist = getManipulators();
    List<CacheManipulator<T>> cm1 = null;
    synchronized(blist) {
      cm1 = new LinkedList<>(blist);
    }
    List<CacheManipulator<T>> bcm1 = cm1;
    blist = getPersistedManipulators();
    synchronized(blist) {
      blist.forEach(one->{if(!bcm1.contains(one))bcm1.add(one);});
    }
    for(CacheManipulator<T> cm:bcm1)
      cm.stopParallelProcessing();
  }
}
