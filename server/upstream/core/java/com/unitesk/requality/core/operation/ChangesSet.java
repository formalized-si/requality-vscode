package com.unitesk.requality.core.operation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.unitesk.requality.core.exception.OrderWasAlreadySetException;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;

public class ChangesSet <T>{
  private Set<String> changed = Collections.emptySet(),
                      removed = Collections.emptySet(),
                      added = Collections.emptySet();
  private Map<String,T> olda = Collections.emptyMap();// = new HashMap<>();
  private Map<String,T> newa = Collections.emptyMap();// = new HashMap<>();
  private List<String> order = null;
  public ChangesSet(){
  }
  public ChangesSet(ChangesSet<T> changesb) {
    if(!changesb.changed.isEmpty())
      changed = new HashSet<>(changesb.changed);
    if(!changesb.removed.isEmpty())
      removed = new HashSet<>(changesb.removed);
    if(!changesb.added.isEmpty())
      added = new HashSet<>(changesb.added);
    if(!changesb.olda.isEmpty())
      olda = new TreeMap<>(changesb.olda);
    if(!changesb.newa.isEmpty())
      newa = new TreeMap<>(changesb.newa);
    if(changesb.order!=null)
      order = new LinkedList<>(changesb.getOrder());
  } 
  public ChangesSet(Set<String> toadd, Set<String> tomodify, Set<String> todelete,Map<String,T>old,
      Map<String,T>newa,
      List<String> order) {
    changed = tomodify;
    if(changed == null)
      changed = Collections.emptySet();
    removed = todelete;
    if(removed == null)
      removed = Collections.emptySet();
    added = toadd;
    if(added == null)
      added = Collections.emptySet();
    this.order = order;

    this.olda = old;
    if(this.olda==null)
      this.olda = Collections.emptyMap();
    this.newa = newa;
    if(newa==null)
      this.newa = Collections.emptyMap();
  }
  public List<String> getOrder() {
    return order;
  }
  public void setOrder(List<String> ord) throws OrderWasAlreadySetException{
    if(order!=null)
      throw new OrderWasAlreadySetException();
    order = ord;
  }
  public void resetOrder(){
    if(order == null)
      RLogger.logInfo("Excess reset order");
    order = null;
  }
 
  private void addChange(String name, T o, T n) throws OrderWasAlreadySetException{
    if(order!=null)
     throw new OrderWasAlreadySetException();
    if(newa.isEmpty())
      newa = new HashMap<>();
    newa.put(name, n);
    if(added.contains(name))
      return;
    if(olda.isEmpty())
      olda = new HashMap<>();
    if(!olda.containsKey(name))
      olda.put(name, o);
    if(removed.contains(name))
      removed.remove(name);
    if(changed.isEmpty())
      changed = new HashSet<>();
    changed.add(name);
  }
  private void addCreate(String name, T o, T n) throws OrderWasAlreadySetException{
    if(order!=null)
      throw new OrderWasAlreadySetException();
    if(olda.isEmpty())
      olda = new HashMap<>();
    if(!olda.containsKey(name))
      olda.put(name, o);
    if(newa.isEmpty())
      newa = new HashMap<>();
    newa.put(name, n);
    if(removed.contains(name)){
      removed.remove(name);
    }
    if(changed.contains(name)){
      changed.remove(name);
    }
    //if(!changed.contains(name)){
    if(added.isEmpty())
      added = new HashSet<>();
    added.add(name);
  //  }
  }
  private void addRemoved(String key, T o) throws OrderWasAlreadySetException{
    if(order!=null)
      throw new OrderWasAlreadySetException();
    if(olda.isEmpty())
      olda = new HashMap<>();
    if(!olda.containsKey(key))
      olda.put(key, o);
    if(newa.isEmpty())
      newa = new HashMap<>();
    newa.put(key, null);
    added.remove(key);
    changed.remove(key);
    if(removed.isEmpty())
      removed = new HashSet<>();
    removed.add(key);
  }

  public void addSomeChange(String s, T old, T n) throws OrderWasAlreadySetException {
    if(old == null && n!=null)
      addCreate(s, old, n);
    else
      if(n == null)
        addRemoved(s, old);
      else
        addChange(s,old,n);
    
  }
  public boolean contains(String s) {
    return newa.containsKey(s);
  }
  public Set<String> getRemoved() {
    return removed;
  }
  public Set<String> getChanged() {
    return changed;
  }
  public Set<String> getAdded() {
    return added;
  }
  public boolean isEmpty() {
    return changed.isEmpty()&&added.isEmpty()&&removed.isEmpty();
  }
  public void clear() {
    changed.clear();
    added.clear();
    removed.clear();
    olda.clear();
    newa.clear();
    order = null;
  }
  public ChangesSet<T> merge(ChangesSet<T> changesb) throws OrderWasAlreadySetException {
    ChangesSet<T> ns = new ChangesSet<>();
    List<String> order = changesb.getOrder();
    if(order == null)
      order = Collections.emptyList();
    if((this.order!=null&&!this.order.isEmpty())||!order.isEmpty())
      order = new LinkedList<>(order);
    if(this.order!=null)
      for(String s:this.order) {
        if(order.contains(s))
          order.remove(s);
        order.add(s);
      }
    for(String s:order){
      T old = this.getOld(s);
      if(old == null)
        old = changesb.getOld(s);
      T n = changesb.getNew(s);
      if(n == null)
        n = this.getNew(s);
      ns.addSomeChange(s, old, n);
    }
    ns.setOrder(order);
    return ns; 
  }

  public Set<String> getAll() {
    return new HashSet<>(newa.keySet());
  }
  public ChangesSet<T> getReverted() {
    List<String> or = order;
    if(order!=null)
      Collections.reverse(or);
    if(added.contains(TreeNode.ATTR_ID) && added.contains(TreeNode.ATTR_TYPE)){
      Map<String, T> val = new TreeMap<>();
      val.putAll(olda);
      val.putAll(newa);
      return new ChangesSet<>(new HashSet<>(),new HashSet<>(), new HashSet<>(getAll()),new TreeMap<>(),val,or);
    }
    return new ChangesSet<>(new HashSet<>(removed),new HashSet<>(changed),
        new HashSet<>(added),newa,olda,or);
  }
  public T getOld(String key) {
    return olda.get(key);
  }

  public T getNew(String key) {
    return newa.get(key);
  }
  public Map<String, T> getOldMap() {
    return olda;
  }
  public Map<String, T> getNewMap() {
    return newa;
  }
  public void removeKey(String key) {
    removed.remove(key);
    changed.remove(key);
    added.remove(key);
    olda.remove(key);
    newa.remove(key);
    if(order != null)
      order.remove(key);
  }
  
}
