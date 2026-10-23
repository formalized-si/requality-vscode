package com.unitesk.requality.core.events;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public abstract class TreeChangeListener implements ITreeChangeListener {

  @Override
  public void changed(
      TreeNode node, ChangesSet<Attribute> what, Moment m, byte flag) {
    if(m == Moment.AFTER)
      changed(node, what);
  }
  
  @Override
  public void beforeCreated(NodeDesc node, byte flag) {
  }

  @Override
  public void created(TreeNode node, byte flag) {
    created(node);
  }

  @Override
  public void deleted(TreeNode node, byte flag, Moment m) {
    if(m == Moment.AFTER)
      deleted(node);
  }

  @Override
  public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {
    moved(node, oldParent, newParent);
  }

  public abstract void changed(TreeNode node, ChangesSet<Attribute> what);

  public abstract void created(TreeNode node);

  public abstract void deleted(TreeNode node);

  public abstract void moved(TreeNode node, TreeNode oldParent, TreeNode newParent);
}
