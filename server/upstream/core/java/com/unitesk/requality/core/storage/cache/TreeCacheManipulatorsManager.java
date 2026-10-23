package com.unitesk.requality.core.storage.cache;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.CacheInitializationStage;
import com.unitesk.requality.core.storage.LinkResolver;
import com.unitesk.requality.core.storage.TermsManipulator;

public class TreeCacheManipulatorsManager extends CacheManipulatorsManager<TreeDB,TreeDB>{
  private TermsManipulator tm;
  private NewIndexesManipulator nim;
  private LinkResolver lm;
  private ReportProcessedCache repGenCache;
  public void initCM(TreeDB treeDB, SettingsManipulator sm, IProgressMonitor pm){
    if(initedFlag != Status.NOT_INITED)
      return;
    pscm = sm;
    if(!list.contains(sm))
      registerManager(pscm);
    if(lm!=null)
      lm.close();
    lm = new LinkResolver(treeDB);
    if(pm.isCanceled())return;
    registerPersistingManager(lm);
    tm = new TermsManipulator(treeDB);
    if(pm.isCanceled())return;
    registerPersistingManager(tm);
    nim = new NewIndexesManipulator(treeDB);
    if(pm.isCanceled())return;
    registerPersistingManager(nim);
    apiMans = new HashMap<>();
    Map<String, CacheManipulator<TreeDB>> map = 
        API.initAPIDBPersistCacheManipulator(treeDB, sm);
    if(pm.isCanceled())return;
    for(String s:map.keySet()){
      CacheManipulator<TreeDB> cm = map.get(s);
      apiMans.put(s, cm);
      if(pm.isCanceled())return;
      registerPersistingManager(cm);
    }
    map = 
        API.initAPIDBCacheManipulator(treeDB, sm);
    if(pm.isCanceled())return;
    for(String s:map.keySet()){
      CacheManipulator<TreeDB> cm = map.get(s);
      apiMans.put(s, cm);
      if(pm.isCanceled())return;
      registerManager(cm);
    }
    repGenCache = new ReportProcessedCache();
    registerManager(repGenCache);
    initedFlag = Status.INITED;
  }
  public LinkResolver getLinkManipulator() {
    if(initedFlag != Status.INITED)
      throw new RuntimeException("Caches manipulator is not inited!");
    return lm;
  }
  public NewIndexesManipulator getNewIndexesManipulator(){
    if(initedFlag != Status.INITED)
      throw new RuntimeException("Caches manipulator is not inited!");
    return nim;
  }
  
  public TermsManipulator getTermsManipulator() {
    if(initedFlag != Status.INITED)
      throw new RuntimeException("Caches manipulator is not inited!");
    return tm;
  }
  public CacheManipulator<TreeDB> getManipulator(String key) {
    return apiMans.get(key);
  }
  public CacheManipulatorInitializationStage getManipulatorState(String sName, SettingsManipulator settingsStore,
     TreeDB db, CacheInitializationStage full) {
    CacheManipulator<TreeDB> man = getManipulator(sName);
    if(man == null)
      return CacheManipulatorInitializationStage.NOT_INITED;
    return man.getCacheState(settingsStore, db, full);
  }
  public boolean isDisposed() {
	return initedFlag == Status.DISPOSED;
  }
  @Override
  public boolean dropStateAfterClear() {
    return true;
  }
}
