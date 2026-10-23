package com.unitesk.requality.core.operation.document;

import java.util.UUID;
import com.unitesk.requality.core.model.TreeNode;

public class UUIDRoot {
  public UUID uuid;
  public TreeNode root;

  public UUIDRoot(UUID uid, TreeNode rt) {
    this.uuid = uid;
    this.root = rt;
  }
}
