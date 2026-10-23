package com.unitesk.requality.core.propagator;

import com.unitesk.requality.core.api.IAttributeChangePropagator;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.valueprovider.NumericIdProvider;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public class NumericIdPropagator implements IAttributeChangePropagator {

  @Override
  public boolean shouldBeApplied(
      TreeNode node, ChangesSet<Attribute> cs, Moment m) {
    if(m==Moment.BEFORE)
      return false;
    if(cs.contains(NumericIdProvider.ATTR_KEY))
      return true;
    return false;
  }

  @Override
  public boolean apply(TreeNode node, ChangesSet<Attribute> changes, Moment m) {
    if(changes.contains(NumericIdProvider.ATTR_KEY)) {
      for(TreeNode child:node.getWorkChildren()) {
        child.updateLocalAttribute(NumericIdProvider.ATTR_KEY);
        child.commitAttributes();
      }
    }
    return false;
  }
  static IAttributeChangePropagator one = new NumericIdPropagator();
  public static IAttributeChangePropagator getOne() {
    return one;
  }

  @Override
  public int getPriority() {
    return LOW;
  }

  @Override
  public void propagatorInited(TreeNode node) {
    // TODO locations are not inited and label are in not inited state so this is not needed
  }
}
