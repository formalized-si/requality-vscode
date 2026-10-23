package com.unitesk.requality.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.log.RLogger;
//TODO change to be resstorage specific?
public class ListenersController {
  private ReentrantReadWriteLock listenersLock = new ReentrantReadWriteLock();
  private ReadLock listReadLock = listenersLock.readLock();
  private WriteLock listWriteLock = listenersLock.writeLock();
  private Map<String, List<ITreeChangeListener>> listeners = new ConcurrentHashMap<>();
  private Map<String, List<ITreeChangeListener>> guilisteners = new ConcurrentHashMap<>();

  private List<ITreeChangeListener> getListeners(String lname, boolean gui, boolean create) {
    if(lname == null)
      return Collections.emptyList();
    synchronized(listeners) {
      List<ITreeChangeListener> lists = gui?guilisteners.get(lname):listeners.get(lname);
      if(lists == null) {
        if(!create) 
          return Collections.emptyList();//for users
        lists = new CopyOnWriteArrayList<>(); //for addlisteners, mb replace by two lists
        if(gui)
          guilisteners.put(lname, lists);
        else
          listeners.put(lname, lists);
      }
      return lists;
    }
  }
  public boolean addGUIListener(String lname, ITreeChangeListener listener) {
    return addListener(true, lname, listener);
  }
  public boolean addListener(String lname, ITreeChangeListener listener) {
    return addListener(false, lname, listener);
  }
  public boolean removeGUIListener(String lname, ITreeChangeListener listener) {
    return removeListener(true, lname, listener);
  }
  public boolean removeListener(String lname, ITreeChangeListener listener) {
    return removeListener(false, lname, listener);
  }

  public List<ITreeChangeListener> getListeners(String lname) {
    if(lname == null)
      return Collections.emptyList();
    listReadLock.lock();
    try {
      return Collections.unmodifiableList(getListeners(lname, false, false));
    }finally {
      listReadLock.unlock();
    }
  }
  public List<ITreeChangeListener> getGUIListeners(String lname) {
    if(lname == null)
      return Collections.emptyList();
    listReadLock.lock();
    try {
      return Collections.unmodifiableList(getListeners(lname, true, false));
    }finally {
      listReadLock.unlock();
    }
  }
  private boolean addListener(boolean gui, String lname, ITreeChangeListener listener) {
    if (listener == null || lname == null) return false;
    listWriteLock.lock();
    try {
      List<ITreeChangeListener> list = getListeners(lname, gui, true);
      if(!list.contains(listener))
      if(list.add(listener)) {
        if(gui)
            guilisteners.put(lname, list);
          else
            listeners.put(lname, list);
        return true;
        }
      else
        RLogger.logDebug("attempt to add already existing "+(gui?"gui":"")+"listener for "+lname);
      return false;
    }finally {
      listWriteLock.unlock();
    }
  }
  private boolean removeListener(boolean gui, String lname, ITreeChangeListener listener) {
    if (listener == null || lname == null) return false;

    listWriteLock.lock();
    try {
      List<ITreeChangeListener> list = getListeners(lname, gui, false);
      if(list!=null)
        if(list.remove(listener)) {
        if(gui)
            guilisteners.put(lname, list);
          else
            listeners.put(lname, list);
        return true;
        }
    }finally {
      listWriteLock.unlock();
    }
    return false;
  }
}
