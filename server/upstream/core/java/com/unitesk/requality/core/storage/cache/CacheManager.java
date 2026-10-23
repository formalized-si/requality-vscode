package com.unitesk.requality.core.storage.cache;

import com.unitesk.requality.core.model.TreeDB;

public abstract class CacheManager {
  private ICacheStorageProvider cp;
  protected boolean isCp = false;
  //CP case
  public CacheManager(ICacheStorageProvider cp, boolean isCP){
    this.cp = cp;
    this.isCp = isCP;
  }
  public CacheStorage getStorage(TreeDB dbfrom) {
    if(cp!=null)
      if(isCp)
        return cp.getCPCacheStorage(dbfrom);
      else
        return cp.getCacheStorage(dbfrom);
    return null;
  }
}
