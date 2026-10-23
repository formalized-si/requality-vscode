package com.unitesk.requality.core.selection;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.unitesk.requality.core.model.TreeNode;

public class SelectionRecord {
  public static SelectionRecord EMPTY = new SelectionRecord();
  public Collection<Object> fullSelectionObjects;
  public Collection<TreeNode> fullSelection;
  public Map<String, Collection<TreeNode>> selectionByType;
  public SelectionRecord(Collection<TreeNode> fullSel) {
    this(fullSel == null? new TreeNode[0]: fullSel.toArray(new TreeNode[fullSel.size()]));
  }
  public SelectionRecord(Object ... fullSel) {
    toObjectsSelection(fullSel);
    this.fullSelection = Collections.emptyList();
    this.selectionByType = Collections.emptyMap();
  }
  public SelectionRecord(Object[]objs, TreeNode[]nodes) {
    toObjectsSelection(objs);
    toNodesSelection(nodes);
    this.selectionByType = gatherTypedMap(fullSelection);
		
  }
  private final void toObjectsSelection(Object[]fullSel) {
    this.fullSelectionObjects = Collections.emptyList();
    if(fullSel != null && fullSel.length > 0) {
      for(Object o : fullSel)
        if(o!=null) {
          if(this.fullSelectionObjects.isEmpty())
            this.fullSelectionObjects = new ConcurrentLinkedDeque<>();
          this.fullSelectionObjects.add(o);
        }
    }
  }
  private final void toNodesSelection(TreeNode[]fullSel) {
    this.fullSelection = Collections.emptyList();
    if(fullSel != null && fullSel.length > 0) {
      for(TreeNode node:fullSel)
        if(node!=null) {
          if(this.fullSelection.isEmpty())
            this.fullSelection = new ConcurrentLinkedDeque<>();
          this.fullSelection.add(node);
        }
	}

  }
  public SelectionRecord(TreeNode ... fullSel) {
    this.fullSelectionObjects = Collections.emptyList();
    toNodesSelection(fullSel);
    this.selectionByType = gatherTypedMap(fullSelection);
  }
  public SelectionRecord(SelectionRecord sel) {
    if(sel.fullSelection.isEmpty())
      fullSelection = Collections.emptyList();
    else
      fullSelection = new ConcurrentLinkedDeque<>(sel.fullSelection);
    if(sel.fullSelectionObjects.isEmpty())
      fullSelectionObjects = Collections.emptyList();
    else
      fullSelectionObjects = new ConcurrentLinkedDeque<>(sel.fullSelectionObjects);
    selectionByType = gatherTypedMap(fullSelection);
  }
  private static Map<String, Collection<TreeNode>> gatherTypedMap(Collection<TreeNode> sel) {
	if(sel.isEmpty())
      return Collections.emptyMap();
	Map<String, Collection<TreeNode>> typed = new ConcurrentHashMap<>();
	for(TreeNode node:sel)
	{
      String type = node.getType();
      Collection<TreeNode> tsel = typed.get(type);
      if(tsel == null) {
        tsel = ConcurrentHashMap.newKeySet();
        typed.put(node.getType(), tsel);
      }
      tsel.add(node);
	}
	return typed;
  }
  public void removeAll(Collection<TreeNode> rem) {
    Set<String> remType = null;
    for(TreeNode node: rem) {
      String type = node.getType();
      if(fullSelection.remove(node)) {
        Collection<TreeNode>nds = selectionByType.get(type);
        if(nds!=null) {
          if(nds.remove(node) && (remType == null || !remType.contains(type)))
          {
            if(remType == null)
              remType = new HashSet<>();
            remType.add(type);
          }
        }
      }
    }
    if(remType!=null)
      for(String t: remType) {
        Collection<TreeNode>nds = selectionByType.get(t);
        if(nds!=null && nds.isEmpty())
          selectionByType.remove(t);
      }
  }
  public boolean remove(TreeNode node) {
    if(fullSelection.remove(node)) {
      String type = node.getType();
      Collection<TreeNode>nds = selectionByType.get(type);
      if(nds!=null)
        if(nds.remove(node))
          if(nds.isEmpty())
            selectionByType.remove(type);
      return true;
    }
    return false;
  }
  public Collection<TreeNode> getByType(String type) {
    return selectionByType.getOrDefault(type, Collections.emptyList());
  }
  public void replaceAll(Map<TreeNode, Collection<TreeNode>> repl) {
    ConcurrentLinkedDeque<TreeNode> altSet = new ConcurrentLinkedDeque<TreeNode>();
    for(TreeNode node : fullSelection) {
	  Collection<TreeNode> r = repl.get(node);
      if(r!=null) { 
        for(TreeNode altN : r) {
          if(!fullSelection.contains(altN) && !altSet.contains(altN)) {
            altSet.add(altN);
          }
        }
      }else 
        altSet.add(node);
    }
    fullSelection = altSet;
    selectionByType = gatherTypedMap(fullSelection); 
  }
  public Collection<TreeNode> get(String typeName) {
    return selectionByType.get(typeName);
  }
  public Collection<TreeNode> getOrDefault(String typeName, List<TreeNode> emptyList) {
    return selectionByType.getOrDefault(typeName, emptyList);
  }
  public TreeNode getFirstNode() {
    if(fullSelection.size() == 0)
      return null;
    return fullSelection.iterator().next();
  }
  public Object getFirstObject() {
    if(fullSelectionObjects.size() == 0)
      return null;
    return fullSelectionObjects.iterator().next();
  }
  public boolean isEmpty() {
    return fullSelection.size() == 0 && fullSelectionObjects.size() == 0;
  }
  public boolean isSameWith(SelectionRecord sr) {
    if(sr == null)
      return false;
    if(!isSame (fullSelection, sr.fullSelection) || !isSame(fullSelectionObjects, sr.fullSelectionObjects))
      return false;
	return true;
  }
  private boolean isSame(Collection sel1, Collection sel2) {
    if(sel1.size()!=sel2.size())
      return false;
	Iterator it1 = sel1.iterator(),
			it2 = sel2.iterator();
	boolean lastComp = true;
	while(it1.hasNext()) {
      lastComp = it1.next()==it2.next();
      if(!lastComp)
        return false;
	}
    return lastComp;
  }
  
}
