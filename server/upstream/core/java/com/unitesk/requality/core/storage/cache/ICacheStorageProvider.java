package com.unitesk.requality.core.storage.cache;

import com.unitesk.requality.core.model.TreeDB;

public interface ICacheStorageProvider {
  public CacheStorage getCacheStorage(TreeDB dbfrom);
  public CacheStorage getCPCacheStorage(TreeDB dbfrom);
}
