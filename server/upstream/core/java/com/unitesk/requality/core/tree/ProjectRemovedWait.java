package com.unitesk.requality.core.tree;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.DBLocalName;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;

public class ProjectRemovedWait {
  private static Object syncObject = new ProjectRemovedWait();
  private static int WAIT_TIME_DEFAULT = 5*60000;//default wait for deleted is five minutes
  private static Map<Long, List<TimeOutRecord>> timeoutNodes = new TreeMap<>();
  private static Map<String, TimeOutRecord> dbRecords = new TreeMap<>();
  
  private static Map<TreeStateManager, TreeStateManagerRecord> tsmRecords = new HashMap<>();
  
  private static Thread stateUpdateHandler = null;
  
  private static class TreeStateManagerRecord{
    public ReentrantLock tsmLock = new ReentrantLock();
    public TreeStateManager tsm;
    public TreeState[] states;
    private Set<String> removedHook = new HashSet<>();
    public IStateChangeHandler handler = new AAllTreeStateChangeHandler() {
      @Override
      public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState, TreeState state) {
        DBLocalName name = DBLocalName.formLocalNameKey(db, lname, ri==null?RevisionInfo.NO_REV:ri);
        String nameStr = getStringName(name);
        TimeOutRecord tor = null;
        synchronized (syncObject) {
          tor = dbRecords.remove(nameStr);
          if(tor!=null) {
            Set<Long> times;
            synchronized (tor.timeoutRunnables) {
              times = new HashSet<>(tor.timeoutRunnables.keySet());
              tor.timeoutRunnables.clear();
            }
            tor.unregister();
            unregister(tor);
            for(Long en: times) {
              List<TimeOutRecord> rec = timeoutNodes.get(en);
              if(rec != null) {
                rec.remove(tor);
                if(rec.isEmpty())
                  timeoutNodes.remove(en);
              }
            }
          }
        }
        if(state == TreeState.CANCELED && tor!=null) {
         Collection<Runnable> torRuns;
          synchronized (tor.timeoutRunnables) {
            torRuns = new LinkedList<>(tor.timeoutRunnables.values());
          }
          for(Runnable r: torRuns)
            r.run();
          synchronized (tor.timeoutRunnables) {
            tor.timeoutRunnables.clear();
          }
        }
      }
    };
    public Set<TimeOutRecord> waitedMap = new HashSet<>();
    public TreeStateManagerRecord(TreeStateManager tsm) {
      this.tsm = tsm;
    }
    public boolean register(TimeOutRecord rec) {
      tsmLock.lock();
      try {
        if(checkState(rec.name))
          return false;
        waitedMap.add(rec);
        return true;
      }finally {
        tsmLock.unlock();
      }
    }
    public boolean unregister(TimeOutRecord rec) {
      boolean wasInList = false;
      tsmLock.lock();
      try {
        if(waitedMap.remove(rec)) {
          for(TimeOutRecord reca: waitedMap)
            if(reca.name.name.equals(rec.name.name)){
              wasInList = true;
              break;
            }
          if(!wasInList) {//no more records in list for project
            String lname = rec.name.name;
            if(!removedHook.contains(lname)) {
              tsm.removeStateHook(lname, states, handler);
              removedHook.add(lname);
            }
          }
          if(waitedMap.isEmpty())
            tsmRecords.remove(tsm);
      }else
        return false;
      }
      finally {
        tsmLock.unlock();
      }
      return wasInList;
    }
    public boolean checkState(DBLocalName name) {
      TreeState state = tsm.getState(name.name, name.rawRevisionPart);
      return TreeStateManager.isInArray(states, state);
    }
    public void addState(String lname, TreeState readyState) {
      boolean isNew = false;
      tsmLock.lock();
      try {
        if(states == null) {
          states = new TreeState[] {TreeState.CANCELED, readyState};
          isNew = true;
        }

        if(!isNew){
          if(!removedHook.contains(lname)) {
            tsm.removeStateHook(lname, states, handler);
            removedHook.add(lname);
          }
        }
        boolean was = false;
        for(TreeState state: states)
          if(state == readyState)
            was = true;
        if(!was) {
          TreeState[] states2 = new TreeState[states.length+1];
          for(int i = 0;i<states.length;i++)
            states2[i] = states[i];
          states2[states.length] = readyState;
          states = states2;
        }
        {
          tsm.addStateHook(lname, states, handler);
          removedHook.remove(lname);
        }
      }finally {
        tsmLock.unlock();
      }
    }
  }
  
  private static class TimeOutRecord{
    public DBLocalName name;
    public TreeStateManager tsm;
    public Map<Long, Runnable> timeoutRunnables = new HashMap<>();
    public TimeOutRecord(DBLocalName dbName) {
      this.name = dbName;
    }
    public Runnable unregisterRunnable(long time) {
      synchronized (timeoutRunnables) {
        return timeoutRunnables.remove(time);
      }
    }
    public long registerRunnable(Long time, Runnable runnable) {
      synchronized (timeoutRunnables) {
        while(timeoutRunnables.containsKey(time))
          time++;
        timeoutRunnables.put(time, runnable);
        return time;
      }
    }
    public String getStringName() {
      return ProjectRemovedWait.getStringName(name);
    }
    public void run(long time, TreeStateManagerRecord rec) {
      try {
        Runnable r;
        synchronized (timeoutRunnables) {
          r = timeoutRunnables.remove(time);
        }
        if(!rec.checkState(name))
          r.run();
      }catch(Exception e) {
        RLogger.logError(e);
      }
    }
    public void unregister() {
      synchronized(timeoutRunnables) {
        if(timeoutRunnables.isEmpty()) {
          String name = getStringName();
          TimeOutRecord tor = dbRecords.remove(name);
          if(tor!=null) {
            for(Long en: tor.timeoutRunnables.keySet()) {
              List<TimeOutRecord> rec = timeoutNodes.get(en);
              if(rec != null) {
                rec.remove(tor);
                if(rec.isEmpty())
                  timeoutNodes.remove(en);
              }
            }
          }
        }
      }
    }
  }
  private static String getStringName(DBLocalName name) {
    return name.toString();
  }
  public static boolean registerWaitHalted(TreeStateManager tsm, TreeDB db, String lname, RevisionInfo ri, TreeState readyState, Runnable runOnRemoved) {
    return registerWaitHalted(tsm, db, lname, ri, readyState, runOnRemoved, WAIT_TIME_DEFAULT);
  }
  public static boolean registerWaitHalted(TreeStateManager tsm, TreeDB db, String lname, RevisionInfo ri, TreeState readyState, Runnable runOnRemoved, int timeWait) {
    long time = System.currentTimeMillis()+timeWait;//target time
    DBLocalName dbName = DBLocalName.formLocalNameKey(db, lname, ri==null?RevisionInfo.NO_REV:ri);
    synchronized (syncObject) {
      List<TimeOutRecord> existList = timeoutNodes.get(time);
      if(existList == null || existList.isEmpty()) {
        existList = new LinkedList<>();
        timeoutNodes.put(time, existList);
      }
      String dbNameStr = getStringName(dbName);
      TimeOutRecord tor = dbRecords.get(dbNameStr);
      boolean checkOldRuntime = false;
      if(tor == null) {
        tor = new TimeOutRecord(dbName);
        dbRecords.put(dbNameStr, tor);
      }else
        checkOldRuntime = true; 
      existList.add(tor);
      if(checkOldRuntime)
        synchronized (tor.timeoutRunnables) {
          if(tor.timeoutRunnables.values().contains(runOnRemoved))
            return false;//already registered?
        }
      long regTime = tor.registerRunnable(time,runOnRemoved);
      
      TreeStateManagerRecord rec = tsmRecords.get(tsm);
      if(rec == null) {
        rec = new TreeStateManagerRecord(tsm);
        tsmRecords.put(tsm, rec);
      }
      rec.addState(lname, readyState);//add hook here
      
      boolean registered = rec.register(tor);
      if(!registered)
      {
        tor.unregisterRunnable(regTime);
        rec.unregister(tor);
        tor.unregister();
        return false;
      }
    }
    if(stateUpdateHandler == null)
      initStateUpdateHandler();
    return true;
  }
//
//  protected static void removeIfNeeded(TreeStateManager tsm, String lname, IStateChangeHandler handler) { 
//    if(tsmRecords.containsKey(tsm)) {
//      stateListeners.remove(tsm);
//      waitedMap.remove(tsm);
//      tsm.removeStateHook(lname, states.remove(tsm), handler);
//    }
//  }
  private static void initStateUpdateHandler() {
    synchronized (syncObject) {
      if(stateUpdateHandler == null) {
        AtomicReference<Thread> art = new AtomicReference<>();
        Thread nt = new Thread(new Runnable() {
          @Override
          public void run() {
            try {
              boolean stop = false;
              while(!stop) {
                long timeNow = System.currentTimeMillis();
                Entry<Long, List<TimeOutRecord>> ent = null;
                synchronized (syncObject) {
                  stop = timeoutNodes.isEmpty();
                  if(!stop) 
                    ent = timeoutNodes.entrySet().iterator().next();
                }
                if(ent!=null) {
                  long taskTime = ent.getKey();
                  if(taskTime<=timeNow) {
                    //вот не все тут стоит запускать, возможно часть деревьев уже в нужном состоянии - для них пропустить
                    try {
                      synchronized (syncObject) {
                        timeoutNodes.remove(ent.getKey());
                        for(TimeOutRecord trec : ent.getValue()) {
                          TreeStateManagerRecord tsmr = tsmRecords.get(trec.tsm);
                          if(tsmr!=null)
                            tsmr.unregister(trec);
                        }
                      }
                      for(TimeOutRecord trec : ent.getValue()) { 
                        TreeStateManagerRecord tsmr = tsmRecords.get(trec.tsm);
                        if(tsmr!=null)
                          trec.run(ent.getKey(), tsmr);//out of locks
                      }
                      synchronized (syncObject) {
                        for(TimeOutRecord trec : ent.getValue()) {
                          trec.unregister();
                        }
                      }
                    }catch(Exception e) {
                      RLogger.logError(e);
                    }
                  } else
                    try {
                      Thread.currentThread().join(taskTime-timeNow);
                    } catch (InterruptedException e) {
                      RLogger.logDebug(CoreUtils.getTrace(e));
                    }
                 }
              }
            }finally {
              if(timeoutNodes.isEmpty())
                synchronized (syncObject) {
                  if(stateUpdateHandler == art.get())
                    stateUpdateHandler = null;
                }
              else
                initStateUpdateHandler();//reinit
            }
          }
        });
        art.set(nt);
        nt.start();
        stateUpdateHandler = nt;
      }
    }
  }
}
