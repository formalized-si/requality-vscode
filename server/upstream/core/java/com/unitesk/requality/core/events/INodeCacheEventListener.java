package com.unitesk.requality.core.events;

import java.util.UUID;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;

public interface INodeCacheEventListener {
  public void newNodeInCache(UUID uid, TreeNode node);
  public void getCacheRequest(TreeDB db, UUID uid);
}
