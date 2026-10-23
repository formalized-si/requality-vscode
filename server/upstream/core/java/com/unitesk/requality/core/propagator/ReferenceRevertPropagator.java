package com.unitesk.requality.core.propagator;

import java.util.Map;
import java.util.Map.Entry;

import com.unitesk.requality.core.api.IAttributeChangePropagator;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.OFlag;

public class ReferenceRevertPropagator implements IAttributeChangePropagator {

  @Override
  public int getPriority() {
    return HIGHEST;
  }

  @Override
  public boolean shouldBeApplied(
      TreeNode node,
      ChangesSet<Attribute> cs,
      Moment m) {
    if(m == Moment.AFTER)
      return false;
    Map<String,Attribute> newa = cs.getNewMap();
    for(Entry<String, Attribute> en:newa.entrySet())
    {
      Attribute old = cs.getOld(en.getKey());
      Attribute n = en.getValue();
      if(old!=null&&old.isReference()||n!=null&&n.isReference())
        return true;
    }
    return false;
  }

  @Override
  public boolean apply(
      TreeNode node,
      ChangesSet<Attribute> cs, Moment m) {
    node.getTreeDB().getLinkResolver().handleLinksAttributesChanges(node, cs, OFlag.NONE);
    node.getTreeDB().getTracker().getCPLinkResolver(node.getTreeDB()).handleLinksAttributesChanges(node, cs, OFlag.NONE);
    return false;
  }
  static IAttributeChangePropagator one = new ReferenceRevertPropagator();
  public static IAttributeChangePropagator getOne() {
    return one;
  }

  @Override
  public void propagatorInited(TreeNode node) {
    // TODO refs tends to be in proper state or not?
    //second can be when we init project and some more project
    //that is related to this
  }
}
