package com.unitesk.requality.core.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.events.INodeCacheEventListener;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.tool.CoreUtils;

public class NodeCache {
  private Map<UUID, TreeNode> cache = new HashMap<>();
//  private TreeDB db;
  private List<INodeCacheEventListener> list = new LinkedList<>();
  private List<INodeCacheEventListener> paused = null;
  Map<UUID, ReentrantLock> cacheLocks = new HashMap<>();
  Map<UUID, Thread> cacheThreads = new HashMap<>();
  private Set<UUID> loading = new HashSet<>();
  public NodeCache(){
//    this.db = adb;
  }
  public boolean addListener(INodeCacheEventListener l){
    if(paused!=null){
      RLogger.logError("Cannot add listener on paused cache");
      return false;
    }
    synchronized (this) {
      return list.add(l);	
  	}
  }
  public boolean removeListener(INodeCacheEventListener l){
    if(paused!=null){
      RLogger.logError("Cannot remove listener from paused cache");
      return false;
    }
    synchronized (this) {
      return list.remove(l);
    }
  }
  private void fireGetRequest(TreeDB db, UUID uid){
    List<INodeCacheEventListener> la = getListeners();
    for(INodeCacheEventListener l:la)
      l.getCacheRequest(db, uid);
  }
  private void fireNewLoadedNode(UUID uid, TreeNode node){
    List<INodeCacheEventListener> la = getListeners();
	for(INodeCacheEventListener l:la)
      l.newNodeInCache(uid, node);
  }
  public TreeNode get(UUID uuid) {
    return cache.get(uuid);
  }
  public TreeNode getNode(TreeDB db, UUID uuid) {
    fireGetRequest(db, uuid);
    TreeNode node = null;
    ReentrantLock lock = null;
    synchronized (cacheLocks) {
      lock = cacheLocks.get(uuid);
    }
    if (lock != null) {
      int tl1 = 100;
      while (tl1 > 0) {
        node = getCached(db, uuid);
        if(node != null)
          return node;
        try {
          Thread.currentThread().join(10);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        tl1--;
      }
      return null;
    } else {
      synchronized (cacheLocks) {
        lock = cacheLocks.get(uuid);
      }
      if(lock!=null){
        try {
          fireGetRequest(db, uuid);
          lock.lock();
          return get(uuid);
        } finally {
          lock.unlock();
        }
      }
      ReentrantLock rl = new ReentrantLock();
  
      synchronized (cacheLocks) {
        cacheLocks.put(uuid, rl);
        cacheThreads.put(uuid,Thread.currentThread());
      }
      boolean locked = false;
      fireGetRequest(db, uuid);
      int it = 0;
      while (!locked && it<50) {
        it++;
        try {
          locked = rl.tryLock(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          RLogger.logDebug("interrupted!"+ uuid );
          if(CoreUtils.DEBUG_LEVEL>0)
            e.printStackTrace();
        }
        if (locked) {
          node = get(uuid);
          try {
            if (node != null) {
              it = 50;
              //db.cacheLocks.get(uuid).unlock();
  
              synchronized (cacheLocks) {
                if(rl == cacheLocks.get(uuid)){
                  cacheLocks.remove(uuid);
                  cacheThreads.remove(uuid);
                }
              }
              return node;
            }
          }finally {
            rl.unlock();
          }
          locked = true;
        }else{
          if(CoreUtils.DEBUG_LEVEL>3){
            RLogger.logDebug("T1:" ,4);
            RLogger.logDebug(Thread.currentThread().getId()+" "+Thread.currentThread().isAlive()+" "+Thread.currentThread().isInterrupted() ,4);
            RLogger.logDebug(CoreUtils.getTrace(Thread.currentThread().getStackTrace()),4);
            RLogger.logDebug("T2:" ,4);
            RLogger.logDebug(cacheThreads.get(uuid).getId()+" "+cacheThreads.get(uuid).isAlive()+" "+cacheThreads.get(uuid).isInterrupted(),4 ); 
            RLogger.logDebug(CoreUtils.getTrace(cacheThreads.get(uuid).getStackTrace()),4);
            RLogger.logDebug(cacheLocks.get(uuid).isHeldByCurrentThread()?"true":"false",4);
            RLogger.logDebug(cacheThreads.get(uuid).getState().toString(),4);
          }
        }
      }
    }
    return null;
  }
  public boolean isLoading(UUID uid) {
    return loading.contains(uid);
  }
  public boolean inCache(UUID uid) {
    return cache.containsKey(uid);
  }
  public TreeNode loadNode(TreeDB db, UUID uuid, String type, Map<String, AttributeDefinition> attrs, boolean notcheck) throws InterruptedException {
    TreeNode node;
    long l = System.currentTimeMillis();
    AttributeStorage as = AttributeStorage.getCachedStorage(db, uuid);
    TreeNode n = null;
    try{
      if (CoreUtils.DEBUG_LEVEL > 7) {
        RLogger.logDebug(uuid + " preload " + (System.currentTimeMillis() - l));
        l = System.currentTimeMillis();
      }
      if (as != null && as.getNode() != null) {
        n = as.getNode();
      } else {
        loading.add(uuid);
        //db.updateParentCloneParentAttributeDefs(uuid, null, null, db.storage.getParent(uuid), attrs);
        if (CoreUtils.DEBUG_LEVEL > 7) {
          RLogger.logDebug(uuid + " attrs upped " + (System.currentTimeMillis() - l));
          l = System.currentTimeMillis();
        }
        n = db.createFromStorage(uuid, type, attrs);
        if (CoreUtils.DEBUG_LEVEL > 7) {
          RLogger.logDebug(uuid + " item created " + (System.currentTimeMillis() - l));
          l = System.currentTimeMillis();
        }
      }
      if ("/".equals(n.getId())) {
        assert n.parent == null;
        db.root = n;
      }
      //n.attrs().incVersions(attrs.keySet(), attrs, n.getVS()); // TODO look
      n.getVS().saveVers();
      // into
      if (CoreUtils.DEBUG_LEVEL > 7) {
        RLogger.logDebug(uuid + " inc versies " + (System.currentTimeMillis() - l));
        l = System.currentTimeMillis();
      }
      // fix for event loop
      cache.put(n.getUUId(), n);
      if (loading.contains(uuid)) loading.remove(uuid);
      fireNewLoadedNode(uuid, n);
      // update of name cache for Requirements
      // Using for UFName supporting.
      // if(n instanceof Requirement)
      ReentrantLock rl = null;
      synchronized (cacheLocks) {
        rl = cacheLocks.get(uuid);
        if(rl!=null) {
          cacheLocks.remove(uuid);
        }
      }
      if(rl!=null) {
        rl.lock();
        rl.unlock();
      }
      // Get links if they are contained in
      if (CoreUtils.DEBUG_LEVEL > 7) {
        RLogger.logDebug(uuid + " up names " + (System.currentTimeMillis() - l));
        l = System.currentTimeMillis();
      }
      // current node
      // n.updateGenerators();
//        if (!notcheck && getCheckerContainer().isEnabled()) {
//          IMark[] cp = db.getCheckerContainer().check(n);
//          db.setChprobUnit(uuid, cp);
//        }
      if (n instanceof ITreeChangeListener) db.addListener((ITreeChangeListener) n);//TODO check remove
      if (n instanceof INodeChangeListener) n.addChangeListener((INodeChangeListener) n);
    // t = System.currentTimeMillis()-t;
      // if(t>300)
      // RLogger.logInfo("anomaly!");
      // RLogger.logInfo("Load node time: "+(t));
      n.fireLoaded();
      CheckerContainer cc = db.getCheckerContainer();
      if(!notcheck && cc.isEnabled())
        cc.addToCheckQ(n, false);
      return n;
    }
    finally{
      //if(n!=null)
        //n.getWorkChildrenUUIDs(false);//update wc if needed
    }
      // }
    //}
    /*
     * if(getStorage() instanceof TreeInstancer){ TreeDB db =
     * VirtualNode.getBaseTree(this); if(db == null) //TODO ups throw new
     * RuntimeException("node not found: " + uuid);
     * if(db.getRootNodeUUID().equals(uuid)) return
     * db.root!=null?db.getRootNode():db.root;
     * if(!db.isNodeExist(uuid)) throw new
     * RuntimeException("node not found: " + uuid); node = db.getNode(uuid);
     * if (node != null) return node; throw new
     * RuntimeException("node not found: " + uuid); } //one level deeper?
     * skip for now //TreeDB db = VirtualNode.getVirtualTreeDB(this);
     * //if(!db.equals(this)) // return db.getNode(uuid);
     */
  }
  public boolean containsKey(UUID uid) {
    return cache.containsKey(uid);
  }
  public TreeNode remove(UUID uuId) {
    return cache.remove(uuId);
  }
  public TreeNode getCached(TreeDB db, UUID uuid) {
    boolean hasNode = false;
    synchronized (cacheLocks) {
      hasNode = loading.contains(uuid);
    }
    if (hasNode) {
      Lock la = null;
      synchronized(cacheLocks) {
        la = cacheLocks.get(uuid);
      }
      try {
        fireGetRequest(db, uuid);
    	if(la!=null)
          la.lock();
        return cache.get(uuid);
      }finally {
    	if(la!=null)
          la.unlock();
      }
    }
    return null;
  }
  @Deprecated
  public void put(UUID uuid, TreeNode result) {
    cache.put(uuid, result);
  }
  public List<INodeCacheEventListener> getListeners() {
    synchronized (this) {
      return list;
    }
  }
  public boolean pauseListeners() {
    if(paused!=null)
      return false;
    synchronized (this) {
      paused = list;
      list = Collections.emptyList();
    }
    return true;
  }
  public void continueListeners() {
    synchronized (this) {
      list = paused;
      paused = null;
	}
  }
}
