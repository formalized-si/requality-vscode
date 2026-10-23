package com.unitesk.requality.eclipse.views;

import com.unitesk.requality.core.model.ITreeNodeProvider;
import com.unitesk.requality.core.model.TreeNode;

public class GRoot implements ITreeNodeProvider{
  public TreeNode node;
  public GRoot(TreeNode node){
    this.node = node;
  }
  @Override
  public TreeNode getNode() {
    return node;
  }
}