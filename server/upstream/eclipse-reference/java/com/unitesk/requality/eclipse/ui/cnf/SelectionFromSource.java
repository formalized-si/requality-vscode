package com.unitesk.requality.eclipse.ui.cnf;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import com.unitesk.requality.core.model.ITreeNodeProvider;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.FSId;

public class SelectionFromSource extends StructuredSelection implements ITreeNodeProvider{
  private Object object;
  private FSId source;
  public SelectionFromSource(Object sel, FSId source) {
    super(sel);
    this.object = sel;
    this.source = source;
  }
  public SelectionFromSource(Object[] sel, FSId source) {
    super(sel);
    this.object = sel;
    this.source = source;
  }
  public SelectionFromSource(List sel, FSId source) {
    super(sel);
    this.object = sel;
    this.source = source;
  }
  public FSId getBaseEvent() {
    return source;
  }
  public Iterator<Object> iterator(){
    if(object instanceof Object[]) {
      return Arrays.asList((Object[])object).iterator();
    }else
    if(object instanceof Collection) {
      return ((Collection)object).iterator();
    }
    return Arrays.asList(object).iterator(); 
  }
  private static Map<FSId, StructuredSelection> emptyMap = new HashMap<>();
  public static StructuredSelection getEmpty(FSId source2) {
    synchronized (emptyMap) {
      StructuredSelection em = emptyMap.get(source2);
      if(em == null)
      {
        em = new StructuredSelection(new SelectionFromSource(StructuredSelection.EMPTY, source2));
        emptyMap.put(source2, em);
      }
      return em; 
    }
  }
  @Override
  public TreeNode getNode() {
    if(object instanceof TreeNode)
      return (TreeNode)object;
    return null;
  }
  public StructuredSelection toSelection() {
    if(object instanceof List)
      return new StructuredSelection((List)object);
    if(object instanceof Object[])
      return new StructuredSelection((Object[])object);
    return new StructuredSelection(object); 
  }
  public boolean isSame(IStructuredSelection ssel) {
    StructuredSelection so = toSelection();
    Iterator<TreeNode> it1 = so.iterator();
    Iterator<TreeNode> it2 = ssel.iterator();
    while(it1.hasNext() && it1.hasNext()==it2.hasNext()) {
      Object n1 = it1.next();
      Object n2 = it2.next();
      if(n1!=n2)
        return false;
    }
    return it1.hasNext()==it2.hasNext();
  }
  public boolean containsAll(IStructuredSelection ssel) {
    StructuredSelection so = toSelection();
    Iterator it2 = ssel.iterator();
    while(it2.hasNext()) {
      Object n2 = it2.next();
      Iterator it1 = so.iterator();
      boolean was = false;
      while(it1.hasNext()) 
        if(it1.next() == n2)
          was = true;
      if(!was)
        return false;
    }
    return true;
  }

}
