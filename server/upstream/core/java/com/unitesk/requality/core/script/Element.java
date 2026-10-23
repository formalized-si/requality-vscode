package com.unitesk.requality.core.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ITreeNodeMapper;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.SystemNames;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.TreeNodeComparator;

public class Element {
  private ElementMapper em;
  private TreeNode node;
  private ITreeNodeMapper tnm = new ITreeNodeMapper() {
    @Override
    public Object getResult(TreeNode node) {
      return em.getElement(node);
    }
  };
  //public Element parent;
  public Element(TreeNode node, ElementMapper em){
    this.em = em;
    setTreeNode(node);
  }
  public void setTreeNode(TreeNode node) {
    this.node = node;
  }
  public Element getParent(){ 
    return em.getElement(node.getShownParent());
  }
  public List<String> attrNames(){
    List<String>ret = new LinkedList<>();
    for(Attribute a:node.attrs().getAvailableAttributes().values()){
      if(a!=null){
        String n = SystemNames.getOne(node.getTreeDB()).getVisSysName(a.getKey());
        if(a.isUserVisible() || n!=null){
          if(n == null)
            n = a.getKey();
          if(n != null)
            ret.add(n);
        }
      }
    }
    return ret;
  }
  public Map<String,Object> attrsNotNull(){
    List<String> names = attrNames();
    Map<String, Object> vals = new HashMap<>();
    for(String s:names) {
      Attribute a = node.attrs().getAttribute(s);
      AttributeType atype = a.getType();
      boolean isRef = atype == AttributeType.REFERENCE;
      if(a!=null && a.getValue()!=null) {
        if(isRef || atype == AttributeType.LIST && atype==AttributeType.REFERENCE) {
          if(isRef)
          {
            ReferenceValue rv = (ReferenceValue) a.getValue();
            if(rv!=null && rv.getRaw()!=null && rv.getRaw().trim().length()>0)
              vals.put(s, true);
          }else {
            ListValue lv = (ListValue) a.getValue();
            if(lv.getValues().size()>0)
              vals.put(s, true);
          }
        }else if(!a.getValue().toString().equals(""))
          vals.put(s, true);//also add test for ""?
      }
    }
    return vals;
  }
  public Map<String,Object> attrs(){
    List<String> names = attrNames();
    Map<String, Object> vals = new HashMap<>();
    for(String s:names)
      vals.put(s, attr(s));
    return vals;
  }
  public Object attr(String s){
    String alt = SystemNames.getOne(node.getTreeDB()).getSysName(s);
    if(alt != null)
      s = alt;
    Attribute a = node.attrs().getAttribute(s);
    if(a == null)
      return null;
    return Attribute.getJavaValue(node, a.getType(),a.getValue(), tnm);
  }
  public Object getQID(){
    return node.getQualifiedId();
  }
  public Object getUUID(){
    return node.getUUId();
  }
  public Map<String, Set<Element>> getLinkedMap(){
    Map<String, Set<Element>>sset = new HashMap<>();
    Map<String, Map<TreeNode, String>> l = RelManager.getLinks(node,true);
    for(String s:l.keySet()){
      sset.put(s, new TreeSet<>(new Comparator<Element>() {
        @Override
        public int compare(Element o1, Element o2) {
          return TreeNodeComparator.getComparator(node.getTreeDB()).compare(o1.node, o2.node);
        }}));
      for(TreeNode node:l.get(s).keySet())
        sset.get(s).add(em.getElement(node));
    }
    return sset;
  }
  public List<Element> getLinked(){
    SortedSet<TreeNode> sset = new TreeSet<>(TreeNodeComparator.getComparator(node.getTreeDB()));
    for(Map<TreeNode, String> s: RelManager.getLinks(node, true).values())
      sset.addAll(s.keySet());
    List<Element>els = new ArrayList<>();
    for(TreeNode node:sset)
      els.add(em.getElement(node));
    return els;
  }
  public List<Element> getLinked(String s){
    TreeNode[] nodes = RelManager.getLinks(node,true,s).keySet().toArray(new TreeNode[0]);
    Arrays.sort(nodes,TreeNodeComparator.getComparator(node.getTreeDB()));
    List<Element>els = new ArrayList<>();
    for(int i = 0;i<nodes.length;i++)
      els.add(em.getElement(nodes[i]));
    return els;
  }
  public List<Element> getChildren(){
    List<TreeNode> nodes = node.getSortedShownChildren();
    List<Element>els = new ArrayList<>();
    Iterator<TreeNode> it = nodes.iterator();
    int i = 0;
    while(it.hasNext())
      els.add(em.getElement(it.next()));
    return els;
  }
  @Override
  public String toString() {
    return "*Element "+node.getUserVisibleName()+"*";
  }
}
