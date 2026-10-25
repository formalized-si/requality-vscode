package com.unitesk.requality.core.propagator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.api.IAttributeChangePropagator;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.MapValue;
import com.unitesk.requality.core.attribute.valueprovider.ChildPositionsProvider;
import com.unitesk.requality.core.attribute.valueprovider.ChildStructuredPositionsProvider;
import com.unitesk.requality.core.attribute.valueprovider.StructuredIndexProvider;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.ITreeStorage;

public class PositionChangePropagator implements IAttributeChangePropagator {

  @Override
  public boolean shouldBeApplied(
      TreeNode node, ChangesSet<Attribute> cs,  Moment m) {
    return cs.contains(ChildPositionsProvider.ATTR_KEY) ||
        cs.contains(ChildStructuredPositionsProvider.ATTR_KEY) || 
        cs.contains(TreeNode.ATTR_SHOWN_CHILDREN);
  }

  @Override
  public boolean apply(TreeNode node, ChangesSet<Attribute> changes, Moment m) {
    if(m == Moment.BEFORE && changes.contains(TreeNode.ATTR_SHOWN_CHILDREN)) {
      ITreeStorage storage = node.getTreeDB().getStorage();
      Attribute oldAttr = changes.getOld(TreeNode.ATTR_SHOWN_CHILDREN);
      Attribute newAttr = changes.getNew(TreeNode.ATTR_SHOWN_CHILDREN);
      List<Object> oldVal, newVal;
      if(oldAttr == null)
        oldVal = Collections.emptyList();
      else
        oldVal = ((ListValue) oldAttr.getValue()).getValues(); 
      if(newAttr == null)
        newVal = Collections.emptyList();
      else
        newVal = ((ListValue) newAttr.getValue()).getValues(); 
      boolean oldStruct =  ChildStructuredPositionsProvider.checkIsStructural(node.getTreeDB(), storage, (Collection<Object>) oldVal),
              newStruct = ChildStructuredPositionsProvider.checkIsStructural(node.getTreeDB(), storage, (Collection<Object>) newVal);
      if(oldStruct!=newStruct) {
        TreeNode spar = node.getShownParent();
        if(spar!=null) {
        //updateStructuredPositions(spar, node);
          spar.updateLocalAttribute(ChildStructuredPositionsProvider.ATTR_KEY);
          spar.commitAttributes();
        }
        //spar.commitAttributes();
      }
    }
    if(m==Moment.BEFORE)
      return false;
    boolean changed = false;
    if(changes.contains(ChildStructuredPositionsProvider.ATTR_KEY)) {
      Attribute cpos = changes.getNew(ChildStructuredPositionsProvider.ATTR_KEY);
      MapValue amap = cpos==null?null:cpos.getGValue();
      Attribute oldMap = changes.getOld(ChildStructuredPositionsProvider.ATTR_KEY);
      MapValue omap = oldMap == null?null:oldMap.getGValue();
      Set<String> handled = Collections.emptySet();
      if(amap!=null && amap.getValues()!=null) {
        TreeDB db = node.getTreeDB();
  
        for(Entry<String, Object> en:amap.getValues().entrySet()) {
            TreeNode nde = db.getCachedNodeOrVirt(
                UUID.fromString(en.getKey()));
            if(handled.isEmpty())
              handled = new HashSet<>();
            handled.add(en.getKey());
            if(nde != null) {
              Integer oldPos = omap!=null&&omap.getValues()!=null?(Integer) omap.getValues().get(en.getKey()):null;
              if(oldPos!=null && oldPos!=-1) {
                nde.updateLocalAttribute(StructuredIndexProvider.ATTR_KEY);
                nde.commitAttributes();
                changed = true;
              }
            }
        }
      }
      if(omap!=null && omap.getValues()!=null) {
        TreeDB db = node.getTreeDB();
        for(Entry<String, Object> enold : omap.getValues().entrySet()) {
            if (!handled.contains(enold.getKey()) && enold.getValue()!=null && !enold.getValue().equals(-1))//if index exists before
            {
              TreeNode nde = db.getCachedNodeOrVirt(
                 UUID.fromString(enold.getKey()));
              if(nde!=null) {
                nde.updateLocalAttribute(StructuredIndexProvider.ATTR_KEY);
                nde.commitAttributes();
                changed = true;
              }
            }
        }
      }
    }

    if(changes.contains(ChildPositionsProvider.ATTR_KEY)) {
      Attribute cpos = changes.getNew(ChildPositionsProvider.ATTR_KEY);
      MapValue amap = cpos==null?null:cpos.getGValue();
      Attribute oldMap = changes.getOld(ChildPositionsProvider.ATTR_KEY);
      MapValue omap = oldMap == null?null:oldMap.getGValue();
      Set<String> handled = Collections.emptySet();
      if(amap!=null && amap.getValues()!=null)
        for(Entry<String, Object> en:amap.getValues().entrySet()) {
          TreeNode nde = node.getTreeDB().getCachedNodeOrVirt(
              UUID.fromString(en.getKey()));
          if(handled.isEmpty())
              handled = new HashSet<>();
            handled.add(en.getKey());
          if(nde != null) {
            Integer oldPos = omap!=null && omap.getValues()!=null? (Integer) (omap.getValues().get(en.getKey())):null;
            Integer newPos = (Integer)en.getValue();
            if(oldPos!=newPos) {
              changed = true;
              nde.updateLocalAttribute(TreeNode.ATTR_CHILD_INDEX);
              nde.commitAttributes();
            }
          }
        }
      if(omap!=null && omap.getValues()!=null)
        for(Entry<String, Object> en:omap.getValues().entrySet()) 
          if(!handled.contains(en.getKey())&& en.getValue()!=null && !en.getValue().equals(-1))
          {
            TreeNode nde = node.getTreeDB().getCachedNodeOrVirt(
                UUID.fromString(en.getKey()));
            if(handled.isEmpty())
                handled = new HashSet<>();
              handled.add(en.getKey());
            if(nde != null) {
              changed = true;
              nde.updateLocalAttribute(TreeNode.ATTR_CHILD_INDEX);
              nde.commitAttributes();
            }
          }
    }
	return false;
  }
 
  static IAttributeChangePropagator one = new PositionChangePropagator();
  public static IAttributeChangePropagator getOne() {
    return one;
  }

  @Override
  public int getPriority() {
    return LOW;
  }

  @Override
  public void propagatorInited(TreeNode node) {
    // TODO mb add
    
  }
}
