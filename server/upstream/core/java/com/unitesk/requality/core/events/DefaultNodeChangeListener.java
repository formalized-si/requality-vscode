package com.unitesk.requality.core.events;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public class DefaultNodeChangeListener implements INodeChangeListener {

  @Override
  public void attributeChange(TreeNode source, String what, Attribute old) {
    // TODO Auto-generated method stub
  }

  @Override
  public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
    for(String s:what.getAll())
      attributeChange(source, s, what.getOld(s));
  }

  @Override
  public void deleted(TreeNode source) {
    // TODO Auto-generated method stub
  }

  @Override
  public void moved(TreeNode node, TreeNode oldParent) {
    // TODO Auto-generated method stub
  }

  @Override
  public void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m) {
    // TODO Auto-generated method stub
  }
}
