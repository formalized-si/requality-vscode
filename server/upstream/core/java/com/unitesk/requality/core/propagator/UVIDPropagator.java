package com.unitesk.requality.core.propagator;

import com.unitesk.requality.core.api.IAttributeChangePropagator;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public class UVIDPropagator implements IAttributeChangePropagator {
  
  TreeNode node;
  
  public UVIDPropagator(TreeNode n) {
    node = n;
  }
  
  @Override
  public boolean shouldBeApplied(
      TreeNode node, ChangesSet<Attribute> cs, Moment m) {
    return cs.getAll().contains(TreeNode.ATTR_USERVISIBLEID)&&m==Moment.AFTER;
  }

  @Override
  public boolean apply(TreeNode node, ChangesSet<Attribute> csa, Moment m) {    
    if(this.node.isDisposed())
      return false;
    this.node.updateLocalAttribute(TreeNode.ATTR_USERVISIBLEID);
    this.node.commitAttributes();
    return true;
  }

  //static IAttributeChangePropagator one = new UVIDPropagator();
  public static IAttributeChangePropagator getOne(TreeNode node) {
    return new UVIDPropagator(node);
  }

  @Override
  public int getPriority() {
    return LOWEST;
  }

  @Override
  public void propagatorInited(TreeNode node) {
    // TODO tends not to be updated, those ids are formed by request
  }
}
