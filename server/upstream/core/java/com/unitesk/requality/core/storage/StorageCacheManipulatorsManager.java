package com.unitesk.requality.core.storage;

import java.nio.file.Path;

import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.cache.CacheManipulatorsManager;
import com.unitesk.requality.core.storage.cache.SettingsManipulator;

public class StorageCacheManipulatorsManager extends CacheManipulatorsManager<ITreeStorage, Path> {

  private ResourceMap ucm = null;
  @Override
  public void initCM(ITreeStorage db, SettingsManipulator sm, IProgressMonitor mon) {
    this.pscm=sm;
    if(!list.contains(sm))
      registerManager(pscm);
    if(db instanceof IUUIDsCachedTreeStorage){
      ucm = new ResourceMap((IUUIDsCachedTreeStorage) db);
      registerPersistingManager(ucm);
    }
    initedFlag = Status.INITED;
  }
  public ResourceMap getResourceMan() {
    if(initedFlag != Status.INITED)
      throw new RuntimeException("Cahces manipulator is not inited! ("+initedFlag+")");
    return ucm;
  }
  public SettingsManipulator getSettingsManipulator() {
    //if(!inited)
      //throw new RuntimeException("Cahces manipulator is not inited!");
    return pscm;
  }
  @Override
  public boolean dropStateAfterClear() {
    return true;
  }

}
