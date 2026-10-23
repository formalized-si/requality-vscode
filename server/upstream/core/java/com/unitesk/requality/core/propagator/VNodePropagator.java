package com.unitesk.requality.core.propagator;

import com.unitesk.requality.core.api.IAttributeChangePropagator;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.nodetype.VirtualNode;

public class VNodePropagator implements IAttributeChangePropagator {

  @Override
  public int getPriority() {
    return 40;
  }

  @Override
  public boolean shouldBeApplied(
      TreeNode node,
      ChangesSet<Attribute> cs,
      Moment m) {
    if(m==Moment.BEFORE)
      return false;
    if(node.getType().equals(VirtualNode.TYPE_NAME)){
      boolean state = ((VirtualNode)node).isCollapsed();
      if(cs.getAll().contains(VirtualNode.ATTR_COLLAPSEVNODE))
      {
        Attribute ad = cs.getOld(VirtualNode.ATTR_COLLAPSEVNODE);
        boolean ostate = ad != null && ad.getValue()!=null && Boolean.parseBoolean(ad.getValue().toString());
        if(ostate != state)
          return true;
      }
      return state && cs.getAll().contains(TreeNode.ATTR_SHOWN_CHILDREN);
    }
    return false;
  }

  @Override
  public boolean apply(
      TreeNode node,
      ChangesSet<Attribute> cs, Moment m) {
    node.getTreeDB().fireNodeChildrenOrderChange(node.getShownParent(), OFlag.NONE);//TODO look on
    node.getShownParent().updateLocalAttribute(TreeNode.ATTR_CHILDREN_UUIDS);//one below tends to be updated here
//    node.getShownParent().attrs().updateAttribute(TreeNode.ATTR_CHILDREN);
//    node.getShownParent().attrs().updateAttribute(TreeNode.ATTR_SHOWN_CHILDREN);
    node.getShownParent().commitAttributes();
    return false;
  }
  
  static IAttributeChangePropagator one = new VNodePropagator();
  
  public static IAttributeChangePropagator getOne() {
    return one;
  }

  @Override
  public void propagatorInited(TreeNode node) {
    // TODO init tends to be done without this
    
  }
}
