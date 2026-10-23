package com.unitesk.requality.core.tree;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.repo.DBSlice;
import com.unitesk.requality.repo.RepoManager;
import com.unitesk.requality.repo.RepoTracker;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;

public class TreeStateManager {
  private static final RevisionInfo NO_REV = RevisionInfo.NO_REV;
  public static final String ALL_CONST = "*";
  //private static Map<IResTracker,TreeStateManager> one = new HashMap<>();
  //Map from local name to state
  private Map<String,ReentrantReadWriteLock> stateLocks =
      new ConcurrentHashMap<String, ReentrantReadWriteLock>();
  private Map<String, Map<String, TreeState>> treeState = new ConcurrentHashMap<>();
  //private Map<TreeDB, Boolean> virtualTreeState = new ConcurrentHashMap<>();
  private Map<String, TreeDB> stateGoodTree = new ConcurrentHashMap<>();
  //private Map<TreeDB, RevisionInfo> treeRevInfo = new ConcurrentHashMap<>();
  private Map<String, Map<TreeState, IStateChangeHandler[]>> stateHooks = new ConcurrentHashMap<String, Map<TreeState,IStateChangeHandler[]>>();
  
  private TreeStateManager(){
  }
  
  public TreeState getState(String lname, RevisionInfo ri) {
    ReadLock rl = getLock(lname).readLock();
    try {
      rl.lock();
      if(ri == null || ri.getRevision() == null || ri.getRevision().equals("NONE"))
        ri = NO_REV;
      return treeState.getOrDefault(lname,Collections.emptyMap()).getOrDefault(ri.getRevision(), TreeState.NOT_LOADED);
    }finally {
      rl.unlock();
    }
  }
  
  public static TreeStateManager getOne(IResTracker rt) {
    return rt.getTreeStateManager();
  }
  
  public ReentrantReadWriteLock getLock(String lname) {
    ReentrantReadWriteLock tl = stateLocks.get(lname);
    if(tl == null) {
      tl = new ReentrantReadWriteLock();
      stateLocks.put(lname, tl);
    }
    return tl;
  }

  public void setState(String lname, String pname, TreeDB db, TreeState state){
    RevisionInfo revInfo = getRevisionInfo(db);
    setState(lname, pname, db, revInfo, state, true);
  }
  public void setState(String lname, String pname, TreeDB db, RevisionInfo revInfo, TreeState state){
    setState(lname, pname, db, revInfo, state, true);
  }
  public void setState(String lname, String pname, TreeDB db, RevisionInfo revInfo, TreeState state, boolean fireEvents){
    Map<TreeState, IStateChangeHandler[]> map = null, map2 = null;
    boolean fireChange = true;//for git revisions it is aslo required sometimes
    TreeState oldState;
    TreeDB storedb = db;
    synchronized (this) {
       if(revInfo == null || revInfo.getRevision(db) == null || revInfo.getRevision(db).equals("NONE"))
        revInfo = NO_REV;
      RLogger.logDebug("set state for "+lname+" ("+revInfo.getRevision()+") "+state+"["+(db!=null?db.toString():"null tree")+"]",9);
      RLogger.logDebug(CoreUtils.getTrace(Thread.currentThread().getStackTrace()),9);
      WriteLock wl = getLock(lname).writeLock();
      wl.lock();
      oldState = getState(lname, revInfo);
      if(oldState == state) {
        wl.unlock();
        return;
      }
      try{
        String name = lname;
        if(oldState == TreeState.READY && revInfo==NO_REV) {
          if(db!=null && stateGoodTree.get(name)!=db)
            RLogger.logInfo("treee override situation for ready state on state manager for "+name);
          else
            stateGoodTree.remove(name);
        }
        if(state == TreeState.READY && revInfo==NO_REV){
          TreeDB store = stateGoodTree.get(name);
          if(storedb == null)
            storedb = store;
          if(store==db || store == null){
            if(!treeState.containsKey(lname))
              treeState.put(lname, new ConcurrentHashMap<String, TreeState>());
            treeState.get(lname).put(revInfo.getRevision(), state);
//            if(revInfo==NO_REV)
//              fireChange = true;
            if(db != null && name!=null)
              stateGoodTree.put(name, db);
            else 
              RLogger.logDebug("attempt to set ready state on null tree");
          }else 
            throw new RuntimeException("Cannot be two treedbs with same name in ready state");
        }else{
          if(!treeState.containsKey(lname))
            treeState.put(lname, new ConcurrentHashMap<String, TreeState>());
          treeState.get(lname).put(revInfo.getRevision(), state);
//          if(revInfo==NO_REV)
//            fireChange = true;
        }
      }
      finally{
        wl.unlock();
      }
      if(fireChange) {
        Map<String,TreeState>mp = treeState.get(lname);
        if(mp!=null)
          synchronized (mp) {
            try{
              mp.notifyAll();
            }
            catch(IllegalMonitorStateException imse){
              RLogger.logError(imse);
            }
          }
        if(fireEvents)  
        {
          map = stateHooks.get(lname);
          map2 = stateHooks.get(ALL_CONST);
        }
      }
    }
    if(fireEvents && fireChange){
      if(map!=null && !map.isEmpty()) {
        synchronized (map) {
          map = new HashMap<TreeState, IStateChangeHandler[]>(map);
        }
        IStateChangeHandler[]rns = map.get(state);
        if(rns!=null) {
          for(IStateChangeHandler r:rns)
            if(r!=null && r.filterTreeDB(storedb))
              r.stateChanges(storedb, lname, pname, revInfo, oldState, state);
        }
      }
      if(map2!=null && !map2.isEmpty()) {
        synchronized (map2) {
          map2 = new HashMap<TreeState, IStateChangeHandler[]>(map2);
        }
        IStateChangeHandler[]rns = map2.get(state);
        if(rns!=null) {
          for(IStateChangeHandler r:rns)
            if(r!=null && r.filterTreeDB(storedb))
              r.stateChanges(storedb, lname, pname, revInfo, oldState, state);
        }
      }
    }
  }

  public static boolean isInArray(TreeState[] states, TreeState state) {
    for(TreeState ts:states)
      if(ts == state)
        return true;
    return false;
  }
 
  public void waitState(String lname, RevisionInfo revisionInfo, TreeState[] state, long time) {
    if(revisionInfo == null)
      revisionInfo = RevisionInfo.NO_REV;
    String srev = revisionInfo.getRevision();
    if(!treeState.containsKey(lname))
      treeState.put(lname, new ConcurrentHashMap<String, TreeState>());
    TreeState snow = treeState.get(lname).get(srev);
    if(isInArray(state, snow))
      return;
    while(!isInArray(state, snow))
      try {
        Map<String, TreeState> st = treeState.get(lname);
        synchronized (st) {
          long targ = -1;
          if(time > -1)
            targ = System.currentTimeMillis()+time;
          while(targ == -1 || targ<System.currentTimeMillis()){
            st.wait(1000);//wait 5 minutes for state
            snow = treeState.get(lname).get(srev);
            if(isInArray(state, snow))
              return;
          }
        }
        snow = treeState.get(lname).get(srev);
      } catch (InterruptedException e) {
        RLogger.logDebug("State user waits for "+lname+" for "+time+" for state but failed");
        snow = TreeState.HALTED;
      }
  }
  public void setStateWithoutDB(String lname, String pname, RevisionInfo ri, TreeState state){
    boolean fireChange = false;
    Map<TreeState, IStateChangeHandler[]> map = null, map2 = null;
    TreeState oldState;
    synchronized (this) {
      if(state == TreeState.READY)
        throw new RuntimeException("Cannot set ready state by non-treedb method");
      if(ri == null)
        ri = NO_REV;
      RLogger.logDebug("set state for "+lname+" ("+ri.getRevision()+") "+state+"[N]",9);
      RLogger.logDebug(CoreUtils.getTrace(Thread.currentThread().getStackTrace()),9);
      WriteLock wl = getLock(lname).writeLock();
      wl.lock();
      oldState = getState(lname, ri);
      if(oldState == state) {
        wl.unlock();
        return;
      }
      if(oldState == TreeState.READY)
        stateGoodTree.remove(lname);
      try{
        if(!treeState.containsKey(lname))
          treeState.put(lname, new ConcurrentHashMap<String, TreeState>());
        treeState.get(lname).put(ri.getRevision(), state);
        if(ri==NO_REV)
          fireChange = true;
      }
      finally{
        wl.unlock();
      }
      if(fireChange) {
        Map<String, TreeState> maps = treeState.get(lname);
        synchronized (maps) {
          try{
            maps.notifyAll();
          }
          catch(IllegalMonitorStateException imse){
            RLogger.logError(imse);
          }
        }
        map = stateHooks.get(lname);
        map2 = stateHooks.get(ALL_CONST);
      }
    }
    if(fireChange) {
      if(map!=null) {
        synchronized (map) {
          IStateChangeHandler[]rns = map.get(state);
          if(rns!=null) {
            for(IStateChangeHandler r:rns)
              if(r!=null) {
                TreeDB db = stateGoodTree.get(lname);
                //if(db!=null) //db can be null and it seems to be correct for some scenarious
                r.stateChanges(db, lname, pname, ri, oldState, state);
              }
          }
        }
      }
      if(map2!=null) {
        synchronized (map2) {
          IStateChangeHandler[]rns = map2.get(state);
          if(rns!=null) {
            for(IStateChangeHandler r:rns)
              if(r!=null){
                TreeDB db = stateGoodTree.get(lname);
                if(db!=null) 
                  r.stateChanges(db, lname, pname, ri, oldState, state);
              }
          }
        }
      }
    }
  }
  
  public void setIsLoaded(String lname, String pname, TreeDB db, RevisionInfo ri) {
    setState(lname, pname, db, ri, TreeState.READY, true);
  }
  
  public boolean isFullReady(String lname, RevisionInfo ri) {
    ReadLock rl = getLock(lname).readLock();
    try {
      if(!rl.tryLock(5, TimeUnit.SECONDS))
        return false;
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    try{
      TreeState st = getState(lname, ri);
      return st == TreeState.READY;
    }finally{
      rl.unlock();
    }
  }
  
  public boolean isReadyNoCache(String lname, RevisionInfo ri) {
    ReadLock rl = getLock(lname).readLock();
    try {
      if(!rl.tryLock(5, TimeUnit.SECONDS))
        return false;
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    try{
      TreeState st = getState(lname, ri);
      return st == TreeState.LOADED_NO_CACHE;
    }finally{
      rl.unlock();
    }
  }
  
  public boolean isBusy(String lname, RevisionInfo ri) {
    return isBusy(lname, ri, 100);
  }
  public boolean isBusy(String lname, RevisionInfo ri, int milisec) {
    ReadLock rl = getLock(lname).readLock();
    try {
      if(!rl.tryLock(milisec, TimeUnit.MILLISECONDS))
        return true;
    } catch (InterruptedException e) {
      RLogger.logDebug("Interrupt during busy lock for 5 sec on "+lname);
    }
    try{
      //TreeDB db = this;
      TreeState ts = getState(lname, ri);
      return ts != TreeState.READY;
    }
    finally{
      rl.unlock();
    }
  }
  
  public TreeDB getBaseTree(TreeDB db){
    if(db == null)
      return null;
    RevisionInfo revInfo = getRevisionInfo(db);
    com.unitesk.requality.core.operation.IProgressMonitor mon = db.getMonitor();//TODO add IDE monitor
    if(revInfo!=null)
      return ResManager.getManager().getTreeDB(null, db.getLocalName(), db.getProjectName(), false, mon);
    //first is null allowing to load non-revisioned one
    return Instantiator.getBaseTree(db);
  }

  public boolean isAvailiable(String lname, RevisionInfo ri) {
    TreeState ts = getState(lname, ri);
    //TODO loading state problems?
    return ts!=TreeState.RESTART&&ts!=TreeState.NOT_LOADED&&ts!=TreeState.DISPOSED&&ts!=TreeState.HALTED&&ts!=TreeState.CANCELED&&ts!=TreeState.LOADING;
  }

  

  public String getRevisionOrHeadString(TreeDB db) {
    RevisionInfo rs = getRevisionInfo(db);
    if(rs!=null) {
      String ri = rs.getRevision();
      if(ri!=null)
        return ri;
    }
    RevisionInfo ri = Global.getRepoManager(db).getHeadRevision();
    if(ri!=null)
      return "~"+ri.getRevision();
    return "";
  }
  
  public static RevisionInfo getRevisionInfo(TreeDB db){
    if(db==null)
      return null;
    RevisionInfo ri = db.revision;
    String rilocal = db.getLocalRevisionString();
    if(ri == null || ri==NO_REV || rilocal == null || rilocal.equals("NONE"))
      return null;
    return ri;
  }
  
  public void setRevisionInfo(TreeDB db, RevisionInfo ri){
    if(!(db.getTracker() instanceof RepoManager)&&!(db.getTracker() instanceof RepoTracker))
      return;
    if(ri == null || ri.getRevision(db) == null || ri.getRevision(db).equals("NONE"))
      ri = NO_REV;
    String lname = db.getLocalName();
    String pname = db.getProjectName();
    db.revision = ri;
    TreeState state = getState(lname, db.revision);
    if(state!=null) {
      setState(lname, pname, db, ri, state, false);
    }
  }

  public String getRevisionString(TreeDB db) {
    RevisionInfo revinfo = getRevisionInfo(db);
    if(revinfo!=null){
      String rev = revinfo.getRevision(db);
      if(rev!=null)
        return rev;
      return "";
    }
    return "";
  }
  private static String META_RELATED_SLICE="RELATED_SLICE";
  private static String META_VIRTUAL_STATE="IS_VIRTUAL";
  public static DBSlice getSliceInfo(TreeDB db) {
    if(db == null)
      return DBSlice.EMPTY;
    DBSlice dbs = db.getMetaObject(META_RELATED_SLICE);
    if(dbs == null)
      dbs = DBSlice.EMPTY;
    return dbs;
  }
  public static void setSliceInfo(TreeDB db, DBSlice slice) {
    if(db == null)
      return;
    if(slice == null || DBSlice.EMPTY == slice)
      db.registerMetaObject(META_RELATED_SLICE, null);
    else
      db.registerMetaObject(META_RELATED_SLICE, slice);
  }
  public boolean isVirtual(TreeDB db) {
    if(db == null)
      return false;
    Boolean state = db.getMetaObject(META_VIRTUAL_STATE);
    if(state == null)
      return false;
    return state;
  }

  public void setVirtualState(TreeDB instTree, boolean b) {
    if(instTree == null)
      return;
    if(!b)
      instTree.registerMetaObject(META_VIRTUAL_STATE, null);
    else
      instTree.registerMetaObject(META_VIRTUAL_STATE, b);
  }

  public boolean canStartTransaction(String lname, RevisionInfo ri) {
    TreeState ts = getState(lname, ri);
    return ts == TreeState.READY;//for now transactions can be started only in fully loaded state
  }
  public void setPartiallyReady(String lname, String pname, TreeDB db, RevisionInfo ri) {
    WriteLock wl = getLock(lname).writeLock();
    wl.lock();
    try {
      setState(lname, pname, db, ri, TreeState.LOADED_NO_CACHE);
    }finally {
      wl.unlock();
    }
    
  }

  public boolean isDisposed(String lname, RevisionInfo ri) {
    TreeState ts = getState(lname, ri);
    return TreeState.isDisposed(ts);
  }
  public void setDisposed(String lname,String pname, TreeDB db, RevisionInfo ri) {
    setState(lname, pname, db, ri, TreeState.DISPOSED, true);
  }

  public synchronized void addStateHook(String lname, TreeState[] treeStates, IStateChangeHandler runnable) {
    if(lname==null || lname.isEmpty() || lname.equals("*"))
      lname=ALL_CONST;
    if(lname == null || lname.equals(""))
    {
      RLogger.logError("Try to add hook for incorrect project name");
    }
    Map<TreeState,IStateChangeHandler[]> runs = stateHooks.get(lname);
    if(runs == null ) {
      runs = new ConcurrentHashMap<TreeState, IStateChangeHandler[]>();
      stateHooks.put(lname, runs);
    }
    synchronized (runs) {
      for(TreeState ts:treeStates) {
        IStateChangeHandler[]rns = runs.get(ts);
        if(rns!=null)
          for(IStateChangeHandler han: rns)
            if (han == runnable)
              throw new RuntimeException("Hook was already added berore "+runnable);
        if(rns == null)
          rns = new IStateChangeHandler[1];
        else 
          rns = Arrays.copyOf(rns, rns.length+1);
        rns[rns.length-1] = runnable;
        runs.put(ts, rns);
      }
    }
  }
  public synchronized void removeStateHook(String lname, TreeState[] tstate, IStateChangeHandler runn) {
    if(lname==null || lname.isEmpty()||lname.equals("*"))
      lname=ALL_CONST;
    Map<TreeState,IStateChangeHandler[]> runs = stateHooks.get(lname);
    if(runs!=null) {
      synchronized(runs) {
        for(TreeState ts:tstate) {
          IStateChangeHandler[]rns = runs.get(ts);
          if(rns == null)
            continue;
          else 
          {
            if(rns.length == 0) {
              RLogger.logError("tries to remove state hook when no hook were added to state "+ts+" on "+lname);
              continue;
            }
            boolean was = false;
            for(IStateChangeHandler r:rns)
              if(r==runn) {
                was = true;
                break;
              }
            if(was) {
              IStateChangeHandler[]nr = new IStateChangeHandler[rns.length-1];
              int i = 0;
              for(IStateChangeHandler r:rns) {
                if(r!=runn)
                  nr[i++] = r;
                else 
                  was = true;
              }
              if(was)
                runs.put(ts, nr);
            }else
              runs.put(ts, rns);
          }
        }
      }
    }
  }

  public static TreeStateManager initOne(IResTracker abstractResTracker) {
    return new TreeStateManager();
  }
  private ConcurrentHashMap<String, Set<RevisionInfo>> pcaches = new ConcurrentHashMap<>();//TODO remove?
  public void markCachesInited(IResTracker tr, String name, RevisionInfo ri) {
    if(ri == null)
      ri = RevisionInfo.NO_REV;
    synchronized (pcaches) {
      Set<RevisionInfo> ol = pcaches.get(name);
      if(ol == null)
      {
        if(ol == null) {
          ol = ConcurrentHashMap.newKeySet();
          pcaches.put(name, ol);
        }
      }
      ol.add(ri);
    }
  }
  public void resetCachesInitedState(IResTracker tr, String name, RevisionInfo ri) {
    if(ri == null)
      ri = RevisionInfo.NO_REV;
    synchronized (pcaches) {
      Set<RevisionInfo> ol = pcaches.get(name);
      if(ol != null)
        ol.remove(ri);
    }
  }
  public boolean areCachesInited(IResTracker tr, String name, RevisionInfo ri) {
    if(ri == null)
      ri = RevisionInfo.NO_REV;
    synchronized (pcaches) {
      return pcaches.getOrDefault(name, Collections.emptySet()).contains(ri);
    }
  }

  public void printHooksStatus(TreeDB db) {
    try {
      Field field = getClass().getDeclaredField("stateHooks");
      field.setAccessible(true);
      Map<String, Map<TreeState, IStateChangeHandler[]>>hooks = (Map<String, Map<TreeState, IStateChangeHandler[]>>) field.get(this);
      System.out.println("HOOKS_ALL");
      Map<TreeState, IStateChangeHandler[]> hooksl = hooks.get("*");
      for(Entry<TreeState, IStateChangeHandler[]> en: hooksl.entrySet()) {
         System.out.println(en.getKey());
         for(IStateChangeHandler s:en.getValue())
           System.out.println("\t"+s.toString());
      }
      System.out.println("_end_");
      System.out.println("HOOKS_"+db.getLocalName());
      hooksl = hooks.get(db.getLocalName());
      for(Entry<TreeState, IStateChangeHandler[]> en: hooksl.entrySet()) {
         System.out.println(en.getKey());
         for(IStateChangeHandler s:en.getValue())
           System.out.println("\t"+s.toString());
      }
      System.out.println("_end_");
    }catch(Exception e) {
      RLogger.logError(e);
    }
  }

}
