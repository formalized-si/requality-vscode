package com.unitesk.requality.core.api;

import java.net.URI;

import com.unitesk.requality.core.model.TreeDB;

public interface IApiCacheClearListener {
  public void cacheCleared(TreeDB dbold, TreeDB dbnew, URI location);
}
