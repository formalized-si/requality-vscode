package com.unitesk.requality.core.propagator;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.unitesk.requality.core.api.IAttributeChangePropagator;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.OFlag;

public class ClonePropagator extends StructChangePropogator {

  @Override
  public boolean shouldBeApplied(
      TreeNode node, ChangesSet<Attribute> cs, Moment m) {
    if(!cs.isEmpty()&&m==Moment.AFTER)
    {
      for(String key:cs.getAll()) {
        Attribute a = cs.getNew(key);
        if(a == null)
          a = cs.getOld(key);
        if(a!=null && !a.isVirtual())//TODO look in the future for changes with no attributes
          return true;
      }
    }
    return false;
  }

  @Override
  public boolean apply(TreeNode node, ChangesSet<Attribute> csa, Moment m) {
    List<String> toremove = Collections.emptyList();
    for(String key: csa.getAll()) {
      Attribute a = csa.getNew(key);
      if(a == null)
        a = csa.getOld(key);
      if(a!=null && a.isVirtual()) {
        if(toremove.isEmpty())
          toremove = new LinkedList<>();
        toremove.add(key);
      }
    }
    ChangesSet<Attribute> gcs = new ChangesSet<>(csa);//TODO add generic for no removes
    if(csa.contains(AttributesVersioningRule.ATTR_NODE_VERSION)) 
      gcs.removeKey(AttributesVersioningRule.ATTR_NODE_VERSION);
    for(String s:toremove)
      gcs.removeKey(s);
    for(UUID uid:Instantiator.getClones(node.getUUId(),node.getTreeDB())){
      if(node.getTreeDB().isLoaded(uid)){
        boolean alt = false;
        TreeNode clone = node.getTreeDB().getNode(uid);
        if(clone.isDisposed())
          continue;
        ChangesSet<Attribute> cs = gcs;
        //TODO filter
        for(String k : gcs.getOrder()){
          Attribute a = clone.attrs().getAttribute(k);
          if(a!=null && (a.getOrigin().equals(uid) || a.isVirtual())) {
            if(!alt) {
              alt = true;
              cs = new ChangesSet<>(gcs);//TODO add generic for no removes
            }
            cs.removeKey(k);
          }
        }
        clone.getTreeDB().getDeltaTarget().changeAttributes(uid, cs, OFlag.NONE);//TODO test it
        for(String s:toremove)
          clone.updateLocalAttribute(s);
        clone.commitAttributes();
      }
    }
    return true;
  }

  static IAttributeChangePropagator one = new ClonePropagator();
  public static IAttributeChangePropagator getOne() {
    return one;
  }

  @Override
  public int getPriority() {
    return LOWEST;
  }

  @Override
  public void propagatorInited(TreeNode node) {
    // TODO on initiation clones am to be in correct state
  }
}
