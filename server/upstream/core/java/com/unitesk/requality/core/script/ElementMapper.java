package com.unitesk.requality.core.script;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.unitesk.requality.core.model.TreeNode;

public class ElementMapper {
  public Map<UUID, Element> maps = new HashMap<>();
  ReadWriteLock rwl = new ReentrantReadWriteLock();
  public Element getElement(TreeNode node){
    Lock l = rwl.readLock();
    l.lock();
    try {
      Element e = maps.get(node.getUUId());
      if(e!=null) {
        e.setTreeNode(node);
        return e;
      }
    }finally {
      l.unlock();
    }
    l = rwl.writeLock();
    l.lock();
    try {
      Element e = maps.get(node.getUUId());
      if(e!=null) {
        e.setTreeNode(node);
        return e;
      }
      e = new Element(node, this);
      maps.put(node.getUUId(), e);
      return e;
    }finally {
      l.unlock();
    }
  }
}
