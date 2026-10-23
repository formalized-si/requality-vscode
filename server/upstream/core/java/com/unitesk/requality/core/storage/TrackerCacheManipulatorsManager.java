package com.unitesk.requality.core.storage;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.cache.CacheManipulatorsManager;
import com.unitesk.requality.core.storage.cache.SettingsManipulator;

public class TrackerCacheManipulatorsManager extends CacheManipulatorsManager<IResTracker, TreeDB> {
  private CPLinkResolver lr;
  private TermsManipulator tm;
  @Override
  public void initCM(IResTracker rt, SettingsManipulator sm, IProgressMonitor mon) {
    pscm = sm;
    if(mon.isCanceled()) return;
    if(!list.contains(pscm))
      this.registerManager(pscm);
    this.lr = new CPLinkResolver(rt);
    if(mon.isCanceled()) return;
    this.registerPersistingManager(lr);
    this.tm = new TermsManipulator(rt);
    if(mon.isCanceled()) return;
    this.registerPersistingManager(tm);
    if(mon.isCanceled()) return;
    initedFlag = Status.INITED;
  }

  public CPLinkResolver getLinkManipulator() {
    if(initedFlag != Status.INITED)
      throw new RuntimeException("Cahces manipulator is not inited!");
    return lr;
  }
  public TermsManipulator getTermsManipulator() {
    if(initedFlag != Status.INITED)
      throw new RuntimeException("Cahces manipulator is not inited!");
    return tm;
  }

  @Override
  public boolean dropStateAfterClear() {
    return false;
  }
  
}
