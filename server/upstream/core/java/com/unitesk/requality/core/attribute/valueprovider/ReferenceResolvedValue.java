package com.unitesk.requality.core.attribute.valueprovider;

import com.unitesk.requality.core.model.TreeNode;

public class ReferenceResolvedValue{
  public String version, value;
  public TreeNode node;
  public ReferenceResolvedValue(String value, TreeNode node, String vers) {
    this.value = value;
    this.node = node;
    this.version = vers;
  }
}