package com.unitesk.requality.core.attribute.reference;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.LateResolveRequest;
import com.unitesk.requality.core.storage.TermsManipulator;
import com.unitesk.requality.core.storage.cache.CacheStorage;

public class RelManager {

  
  public static Map<String, Map<TreeNode,String>> getTermsLinks(TreeNode node){
    return node.getTreeDB().getTermsStorage().getUseToDefTermsRelations(node, null);
  }
    
  public static Map<String, Map<TreeNode,String>> getLinks(TreeNode node, boolean withterms){
    Map<String, Map<TreeNode,String>> map1 =
      node.getTreeDB().getLinkResolver().getLinkedNodes(node);
    if(map1==null || map1.isEmpty())
      map1 = new HashMap<>();
    if(withterms)
      map1.putAll(getTermsLinks(node));
    Map<String, Map<TreeNode, String>> cpmap = node.getTreeDB().getTracker().getCPLinkResolver(node.getTreeDB()).getLinkedNodes(node);
    if(cpmap!=null && !cpmap.isEmpty())
      map1.putAll(cpmap);
    return map1;
  }

  public static Map<TreeNode, String> getLinks(TreeNode node, boolean withterms, String s){
    return getLinks(node, withterms).get(s);
  }

  public static Map<String, Map<TreeNode,String>> getTermsRevertLinks(TreeNode node, boolean withcp){
    return TermsManipulator.getDefToUseTermsRelations(node, withcp);
  }
  public static Map<TreeNode,String> getTermsRevertLinks(TreeNode node, boolean withcp, String key){
    return TermsManipulator.getDefToUseTermsRelations(node, withcp, key);
  }
  //TODO call by key
  /*
  public static Map<String, Map<TreeNode,String>> getTermsRevertLinks(TreeNode node, boolean withcp, String key){
  }*/
  
  public static Map<UUID,String> getRevertLinks(TreeDB db, UUID node, boolean withcp, String key){
    Map<UUID, String> map1 =
        db.getLinkResolver().getLinksToNode(db, node, key);
    if(map1==null || map1.isEmpty())
      map1 = Collections.emptyMap();
    if(withcp){
      if(map1.isEmpty())
        map1 = new HashMap<>();
      Map<UUID, String> cpmap = db.getTracker().getCPLinkResolver(db).getLinksToNode(db, node, key, true);//TODO mb bug?s
      if(cpmap!=null && !cpmap.isEmpty())
        map1.putAll(cpmap);
    }
    return map1;
  }
  public static Map<String, Map<TreeNode,String>> getRevertLinks(TreeNode node, boolean withterms, boolean withcp){
    Map<String, Map<TreeNode, String>> map1 =
        node.getTreeDB().getLinkResolver().getLinksToNode(node, false);
    if(map1==null || map1.isEmpty())
      map1 = new HashMap<>();
    if(withterms) {
      map1.putAll(getTermsRevertLinks(node, withcp));
    }
    if(withcp){
      Map<String, Map<TreeNode, String>> cpmap = node.getTreeDB().getTracker().getCPLinkResolver(node.getTreeDB()).getLinksToNode(node, true, true);
      if(cpmap!=null && !cpmap.isEmpty())
        map1.putAll(cpmap);
    }
    return map1;
  }

  public static Map<TreeNode,String> getRevertLinks(TreeNode node, boolean withterms, boolean withcp, String key){
    Map<TreeNode, String> map1 =
        node.getTreeDB().getLinkResolver().getLinksToNode(node, key, false);
    if(map1==null || map1.isEmpty())
      map1 = new HashMap<>();
    if(withterms) {
      map1.putAll(getTermsRevertLinks(node, withcp, key));
    }
    if(withcp){
      Map<TreeNode, String> cpmap = node.getTreeDB().getTracker().getCPLinkResolver(node.getTreeDB()).getLinksToNode(node, key, true);//TODO mb bug?s
      if(cpmap!=null && !cpmap.isEmpty())
        map1.putAll(cpmap);
    }
    return map1;
  }
  public static Map<TreeNode, String> getRevertLinks(TreeNode node, boolean withterms, String s){
    return getRevertLinks(node,withterms,true,s);
  }
  public static String[] getLinksOrErrorsKeys(TreeNode node) {
    return getLinksOrErrors(node, true).keySet().toArray(new String[0]);
  }
  public static ReferenceValue[] getLinksOrErrorsValues(TreeNode node, String k) {
    return getLinksOrErrors(node, true).get(k);
  }
  public static ReferenceValue getRefVal(TreeNode node, String s, Map<TreeNode, String> map){
	if(map==null || map.isEmpty())
	  map = new HashMap<>();
	return new ReferenceValue(s, new HashMap<>(map),node.attrs(), node.attrs().getNode().getTreeDB().
      getAttributeRules().getReferenceVersionRule());
  }
  public static Map<String, ReferenceValue[]> getLinksOrErrors(TreeNode node, boolean withterms) {
    Map<String, Set<ReferenceValue>>ret = null;
    if(withterms){
      Map<String, Map<TreeNode, String>>links = node.getTreeDB().getTermsStorage().getDefToUseTermsRelations(node,true);
      for(String s:links.keySet()){
        if(ret == null)
          ret = new HashMap<>();
        ret.put(s,new HashSet<>());
        ret.get(s).add(getRefVal(node, s, links.get(s)));
      }
    }
    for(Entry<String, Attribute> en:node.attrs().getActiveAttributes().entrySet()){
      if(en.getValue()!=null && en.getValue().isReference()){
        ReferenceValue[] ta = node.getTreeDB().getLinkResolver().getRefValuesByAttributeName(node, en.getKey());
        if(ta!=null && ta.length>0){
          if(ret == null)
            ret = new HashMap<>();
          if(!ret.containsKey(en.getKey()))
            ret.put(en.getKey(), new HashSet<>());
          for(ReferenceValue rv:ta)
            if(rv.hasErrorsOnResolve(node.attrs()))
              ret.get(en.getKey()).add(rv);
            else
              ret.get(en.getKey()).add(rv);
        }
      }
    }
    if(ret == null || ret.isEmpty())
      return Collections.emptyMap();
    Map<String,ReferenceValue[]> no = new HashMap<>();
    for(Entry<String, Set<ReferenceValue>> en:ret.entrySet())
      no.put(en.getKey(), en.getValue().toArray(new ReferenceValue[0]));
    return no;
  }

  public static Pair<TreeRelationsCounter, TreeRelationsCounter> getRelatedProjects(com.unitesk.requality.core.storage.TrackerCacheManipulatorsManager cmm, CacheStorage cs, String lname, String pname, Map<String, List<LateResolveRequest>> map){
    Pair<TreeRelationsCounter, TreeRelationsCounter> ret = null;
    Pair<TreeRelationsCounter, TreeRelationsCounter> a = cmm.getLinkManipulator().getRelatedProjects(cs, lname, pname);
    if(a!=null&&(!a.getFirst().isEmpty()||!a.getSecond().isEmpty())){
      ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(a.getFirst(), a.getSecond());
    }
    a = cmm.getTermsManipulator().getRelatedProjects(cs, lname, pname);
    if(a!=null){
      boolean afirste = a.getFirst().isEmpty();
      boolean aseconde = a.getSecond().isEmpty();
      if((!afirste||!aseconde)) {
        if(ret == null)
          ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(a.getFirst(), a.getSecond());
        else
        {
          TreeRelationsCounter fc = ret.getFirst();
          if(fc.isEmpty())
            fc = a.getFirst();
          else
            if(!afirste)
              fc = fc.merge(a.getFirst());
          TreeRelationsCounter sc = ret.getSecond();
          if(sc.isEmpty())
            sc = a.getSecond();
          else
            if(!aseconde)
              sc = sc.merge(a.getSecond());
          if(fc != ret.getFirst() || sc != ret.getSecond())
            ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(fc, sc);
        }
      }
    }
    a = cmm.getLinkManipulator().getLateRelatedProjects(cs, lname, pname, map);
    if(a!=null){
      boolean afirste = a.getFirst().isEmpty();
      boolean aseconde = a.getSecond().isEmpty();
      if((!afirste||!aseconde)) {
        if(ret == null)
          ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(a.getFirst(), a.getSecond());
        else
        {
          TreeRelationsCounter fc = ret.getFirst();
          if(fc.isEmpty())
            fc = a.getFirst();
          else
            if(!afirste)
              fc = fc.merge(a.getFirst());
          TreeRelationsCounter sc = ret.getSecond();
          if(sc.isEmpty())
            sc = a.getSecond();
          else
            if(!aseconde)
              sc = sc.merge(a.getSecond());
          if(fc != ret.getFirst() || sc != ret.getSecond())
            ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(fc, sc);
        }
      }
    }
    if(ret == null)
      return TreeRelationsCounter.EMPTY_PAIR;
    return ret;
  }

  public static String DIR_TERM = "directTerms",
                       REV_TERM = "revertTerms",
                       DIR_LINK = "directLinks",
                       REV_LINK = "revertLinks";
  public static Map<String,Map<UUID,Map<String, Map<TreeNode,String>>>> getLinksForNodes(TreeDB db, Set<TreeNode> nodes,boolean withcp) {
    Map<String,Map<UUID,Map<String, Map<TreeNode,String>>>> ret = new
        HashMap<>();
    //dir
    Map<UUID,Map<String, Map<TreeNode,String>>>m1 =
        TermsManipulator.getUseToDefTermsRelationsForNodes(db, nodes,withcp);
    ret.put(DIR_TERM, m1);
    //rev
    Map<UUID,Map<String, Map<TreeNode,String>>>m2 =
        TermsManipulator.getDefToUseTermsRelationsForNodes(db, nodes,withcp);
    ret.put(REV_TERM, m2);
    //dir
    Map<UUID,Map<String, Map<TreeNode,String>>>m3 = 
        db.getLinkResolver().getAllLinkedNodes(nodes);
    Map<UUID,Map<String, Map<TreeNode,String>>>m3b =
        db.getTracker().getCPLinkResolver(db).getAllLinkedNodes(db);
    if(m3b!=null && !m3b.isEmpty()){
      if(m3.isEmpty())
        m3 = new HashMap<>();
      for(UUID uid:m3b.keySet()){
        if(!m3.containsKey(uid))
          m3.put(uid, m3b.get(uid));
        else
          for(String s:m3b.get(uid).keySet())
            if(!m3.get(uid).containsKey(s))
              m3.get(uid).put(s, m3b.get(uid).get(s));
            else
              m3.get(uid).get(s).putAll(m3b.get(uid).get(s));
      }
    }
    ret.put(DIR_LINK, m3);
    //rev
    Map<UUID,Map<String, Map<TreeNode,String>>>m4 =         
        db.getLinkResolver().getAllLinksToNode(nodes);
    Map<UUID,Map<String, Map<TreeNode,String>>>m4b =         
        db.getTracker().getCPLinkResolver(db).getAllLinksToNode(db);//TODO mb bug?s
    if(m4b!=null && !m4b.isEmpty()){
      if(m4.isEmpty())
        m4 = new HashMap<>();
      for(UUID uid:m4b.keySet())
        if(!m4.containsKey(uid)){
          m4.put(uid, m4b.get(uid));
        }else
          for(String s:m4b.get(uid).keySet()){
            if(!m4.get(uid).containsKey(s))
              m4.get(uid).put(s, m4b.get(uid).get(s));
            else
              m4.get(uid).get(s).putAll(m4b.get(uid).get(s));
          }
    }
    ret.put(REV_LINK, m4);
    return ret;
  }
  public static boolean removeReferenceFromList(TreeNode node, TreeNode refTo, String attrName) {
    AttributeStorage as = node.attrs();
    Attribute a = as.getAttribute(attrName);
    ReferenceValueProvider rvp;
    TypedValue tv = null;
    if(a != null){
      IValueProvider vp = a.getDefinition().getValueProvider();
      if(vp == null)
    	throw new RuntimeException("Target attribute "+attrName+" on "+node.getUserVisibleName()+" has null value provider");
      if(!(vp instanceof ReferenceValueProvider))
        throw new RuntimeException("Target attribute "+attrName+" on "+node.getUserVisibleName()+" has non-reference value provider");
      rvp = (ReferenceValueProvider) vp;
      if(rvp.getAttributeType()!=AttributeType.LIST)
       throw new RuntimeException("Target attribute "+attrName+" on "+node.getUserVisibleName()+" is single reference instead of referenvce list");
      tv = a.getTValue();
    }else
      return false;//nothing to remove
    ListValue lv = tv.getGValue();
    List<Object> lo = new LinkedList<>();
    Map<UUID,String> vers = new HashMap<>();
    boolean rem = false;
    for(Object o : lv.getValues()){
      ReferenceValue rv = (ReferenceValue) o;
      for(TreeNode nd:rv.getNodes(as))
        if(nd.getSUUId().equals(refTo.getSUUId())){
          rem = true;
          continue;
        }
      if(rv.getRaw()!=null)
        lo.add(rv.getRaw());
      Map<UUID,String> vm = rv.getVersions();
      if(vm != null)
        vers.putAll(vm);
    }
    if(AttributesVersioningRule.isRefVersEnabled(node.getTreeDB()))
      vers.put(refTo.getUUId(), refTo.getNodeVersion());
    rvp = new ReferenceValueProvider(AttributeType.LIST, 
       new ListValue(AttributeType.REFERENCE, lo));
    for(Object o : lv.getValues()){
      ReferenceValue rv = (ReferenceValue) o;
      rvp.handleRVResolving(as, rv.getRaw(), rv.getStoredNodes(), vers, rv.getWarns(), true); 
    }
    node.attrs().setAttribute(new AttributeDefinition(node.getUUId(), attrName, 
       rvp));
    return rem;
  }
  public static void addReferenceToList(TreeNode node, String refToVal, TreeNode refTo, String attrName) {
    AttributeStorage as = node.attrs();
    Attribute a = as.getAttribute(attrName);
    ReferenceValueProvider rvp;
    TypedValue tv = null;
    if(a == null){
      rvp = new ReferenceValueProvider(AttributeType.LIST, new ListValue(AttributeType.REFERENCE,new LinkedList<>()));
      tv = rvp.getRawValue();
    }else{
      IValueProvider vp = a.getDefinition().getValueProvider();
      if(!(vp instanceof ReferenceValueProvider))
        throw new RuntimeException("Target attribute "+attrName+" on "+node.getUserVisibleName()+" has non-reference value provider");
      rvp = (ReferenceValueProvider) vp;
      if(rvp.getAttributeType()!=AttributeType.LIST)
       throw new RuntimeException("Target attribute "+attrName+" on "+node.getUserVisibleName()+" is single reference instead of referenvce list");
      tv = a.getTValue();
    }
    ListValue lv = tv.getGValue(); 
    List<Object> lo = new LinkedList<>();
    Map<UUID,String> vers = new HashMap<>();
    boolean was = false;
    for(Object o : lv.getValues()){
      ReferenceValue rv = (ReferenceValue) o;
      lo.add(rv.getRaw());
      if(refToVal.equals(rv.getRaw()))
        was = true;
      Map<UUID,String> vm = rv.getVersions();
      if(vm != null)
        vers.putAll(vm);
    }
    if(AttributesVersioningRule.isRefVersEnabled(node.getTreeDB()))
      vers.put(refTo.getUUId(), refTo.getNodeVersion());
    if(!was)
      lo.add(refToVal);
    rvp = new ReferenceValueProvider(AttributeType.LIST, 
       new ListValue(AttributeType.REFERENCE, lo));
    for(Object o : lv.getValues()){
       ReferenceValue rv = (ReferenceValue) o;
     rvp.handleRVResolving(as, rv.getRaw(), rv.getStoredNodes(), vers, rv.getWarns(), true); 
    }
    rvp.handleRVResolving(as, refToVal, new UUID[]{node.getUUId()},
        vers, null, true);
   node.attrs().setAttribute(new AttributeDefinition(node.getUUId(), attrName, 
       rvp));
  }
}