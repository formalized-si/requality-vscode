package com.unitesk.requality.core.propagator;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import com.unitesk.requality.core.api.IAttributeChangePropagator;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

public class LocationLabelPropagator implements IAttributeChangePropagator {

  @Override
  public boolean shouldBeApplied(
      TreeNode node, ChangesSet<Attribute> cs, Moment m) {
    if(m==Moment.BEFORE)
      return false;
    if(node instanceof Requirement&&(cs.contains(Requirement.ATTR_LOCATIONS)||cs.contains(TreeNode.ATTR_USERVISIBLEID)))
      return true;
    return false;
  }

  @Override
  public boolean apply(TreeNode node, ChangesSet<Attribute> changes, Moment m) {
    Attribute ado = changes.getOld(Requirement.ATTR_LOCATIONS);
    Attribute adn = changes.getNew(Requirement.ATTR_LOCATIONS);
    Set<Location> olist = new HashSet<>();
    if(ado != null && ado.getValue() != null){
      ListValue lv = ado.getGValue();
      if(lv != null){
        Iterator<ReferenceValue> it = lv.giterator();
        while(it.hasNext()){
          UUID[] ls = it.next().getNodeUUIDs(node.attrs());
          if(ls!=null&&ls.length>0){
            Location l = (Location)node.getTreeDB().getNode(ls[0]);
            if(l!=null)
              olist.add(l);
          }
        }
      }
    }
    if(adn != null && adn.getValue() != null){
      ListValue lv = adn.getGValue();
      if(lv != null){
        Iterator<ReferenceValue> it = lv.giterator();
        while(it.hasNext()){
          UUID[] ls = it.next().getNodeUUIDs(node.attrs());
          if(ls!=null&&ls.length>0)
          {
            Location l = (Location) node.getTreeDB().getNode(ls[0]);
            if(l!=null)
              olist.add(l);
          }
        }
      }
    }
    if(node instanceof Requirement && changes.contains(TreeNode.ATTR_USERVISIBLEID))
      for(Location l:((Requirement)node).getLocations(true))
        olist.add(l);
    for(Location l:olist){
      if(l != null){
        l.resetLabel();
      }
    }
    return false;
  }
  static IAttributeChangePropagator one = new LocationLabelPropagator();
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
