package com.unitesk.requality.core.api;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public interface IAttributeChangePropagator {
  public static int LOWEST=40, LOW=30, NORMAL=20, HIGH=10, HIGHEST=0;
  public int getPriority();
  public boolean shouldBeApplied(TreeNode node, ChangesSet<Attribute> cs, Moment m);
  public boolean apply(TreeNode node, ChangesSet<Attribute> cs, Moment m);
  public void propagatorInited(TreeNode node);
}
