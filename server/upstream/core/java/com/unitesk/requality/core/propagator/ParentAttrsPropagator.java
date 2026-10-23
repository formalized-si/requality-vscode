package com.unitesk.requality.core.propagator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.api.IAttributeChangePropagator;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.cache.LinksCacheMan;

public class ParentAttrsPropagator extends StructChangePropogator {

  @Override
  public boolean shouldBeApplied(
      TreeNode node, ChangesSet<Attribute> cs,  Moment m) {
    if(m==Moment.BEFORE)
      return false;
    for(String s:cs.getOrder()){
      Attribute olda=cs.getOld(s);
      Attribute newa=cs.getNew(s);
      if(olda!=null&&olda.getAvailability()!=AttributeAvailability.LOCAL||
          newa!=null && newa.getAvailability()!=AttributeAvailability.LOCAL)
        return true;
    }
    return false;
  }

  @Override
  public boolean apply(TreeNode node, ChangesSet<Attribute> changes, Moment m) {
    //Map<String,AttributeDefinition> oldMap = old;
    //getDeltaTarget().changeAttributes(uuid, what, ad);
    TreeDB db = node.getTreeDB();
    LinksCacheMan lcm2 = db.getTracker().getCPLinkResolver(db).getLCM();
    boolean started = false;
    try {
      started = lcm2.initRelatedCache(node);
      List<AttributeDefinition> poolRemove=new LinkedList<>(),poolAdd=new LinkedList<>();
      //LinkedList<String> order = new LinkedList<>();
      //ChangesSet chVis = new ChangesSet();
      //TreeDB.addAttrsRec(node, changes, chVis, order);
      for(String s:changes.getOrder()){
        Attribute olda = changes.getOld(s);
        Attribute newa = changes.getNew(s);
        if(olda!=null && olda.getAvailability()!=AttributeAvailability.LOCAL)
          poolRemove.add(olda.getDefinition());
        if(newa!=null && newa.getAvailability()!=AttributeAvailability.LOCAL){
          poolAdd.add(newa.getDefinition());
        }
      }
      LinkedList<TreeNode> toh = new LinkedList<>();
      for(TreeNode s:node.getActiveWorkChildren())
        toh.add(s);
      int i = 0,j=toh.size();
      Map<String,Attribute> torem = new HashMap<>();
      LinkedList<TreeNode> tos = new LinkedList<>();
      LinkedList<TreeNode> toProp = new LinkedList<>();
      Iterator<TreeNode> it = toh.iterator();
      while(it.hasNext()){
        torem.clear();
        TreeNode n = it.next();
        i++;
        Iterator<AttributeDefinition> itr = poolRemove.iterator();
        boolean changed = false;
        while(itr.hasNext())
        {
          AttributeDefinition ada = itr.next();
          if(ada.getAvailability()==AttributeAvailability.DIRECT_CHILDREN&&ada.isAvaiableOn(n,false)){//TODO test
            Attribute aa = n.attrs().getAttribute(ada.getKey());
            boolean sameLoc = aa!=null && aa.getDefinition().getOrigin().equals(ada.getOrigin());
            if(aa==null || sameLoc){
              torem.put(ada.getKey(), aa);
              changed = true;
            }
          }else{
            Attribute aa = n.attrs().getAttribute(ada.getKey());
            boolean sameLoc = aa!=null && aa.getDefinition().getOrigin().equals(ada.getOrigin());
            if(aa==null || sameLoc){
              torem.put(ada.getKey(), aa);
              changed = true;
            }else
              if(!sameLoc && !toProp.contains(n))
                toProp.add(n);//special case when local attrs are more visible
            //if there are some local attr then we needs to handle subchildrens
          }
        }
        itr = poolAdd.iterator();
        while(itr.hasNext())
        {
          AttributeDefinition ada = itr.next();
          Attribute b = n.attrs().getAttribute(ada.getKey());
          if(ada.getAvailability()==AttributeAvailability.DIRECT_CHILDREN){//TODO test
            if(ada.isAvaiableOn(n, false) &&  
            		(torem.containsKey(ada.getKey())||
            				b==null || 
            				ada.isMoreVisible(db, b.getDefinition(), n.getUUId(), true))) {
              n.attrs().setAttribute(ada);
              changed = true;
            }
          }else{
        	if(ada.getAvailability() == AttributeAvailability.SUBTREE) {
    		  if(b==null || ada.isMoreVisible(node.getTreeDB(), b.getDefinition(), n.getUUId(), false)){
                n.attrs().setAttribute(ada);
                changed = true;
              }else if(!toProp.contains(n))
                toProp.add(n);
        	}
          }
        }
        for(String s:torem.keySet())
          n.attrs().removeAttribute(s);
        if(changed)
          tos.addFirst(n);
      }
      for(TreeNode node1:toProp) 
        apply(node1, changes, m);//propogate change when local attrs are more visible
      for(TreeNode node1:tos)
        node1.commitAttributes();
      return true;
    }finally {
      if(started)
        lcm2.freeRelatedCache(db);
    }
  }
  static IAttributeChangePropagator one = new ParentAttrsPropagator();
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
