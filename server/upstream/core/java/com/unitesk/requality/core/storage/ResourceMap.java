package com.unitesk.requality.core.storage;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.unitesk.requality.core.api.IStoppable;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.cache.CacheManipulator;
import com.unitesk.requality.core.storage.cache.CacheManipulatorInitializationStage;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.storage.cache.SettingsManipulator;
import com.unitesk.requality.tool.CoreUtils;

public class ResourceMap extends CacheManipulator<Path> implements IStoppable{
  private static String NAME="Resource map";
    private static int THREADS_LIMIT = 10;
    private boolean stopped = false;
    private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    ReadLock rl = rwl.readLock();
    WriteLock wl = rwl.writeLock();
    private static String PARAM_NAME="_uuidsCacheInited";
    private Map<Path, UUID> resourceToNode = null;
    private Map<UUID, Path> rresourceToNode = null;
    private IUUIDsCachedTreeStorage rs;
    private int state = -1;
	public boolean legacyMode = false;
    //protected boolean changed = false;
    private static ExecutorService pool;
    static{
      pool = Executors.newFixedThreadPool(THREADS_LIMIT);
    }
    public ResourceMap(IUUIDsCachedTreeStorage rs) {
      this.rs = rs;
    }

    public void writeUnlock() {
      wl.unlock();
    }

    public void writeLock() {
      wl.lock();
    }
    
    @Override
    public CacheManipulatorInitializationStage getCacheState(SettingsManipulator cman, Path treeDB, CacheInitializationStage stage) {
      if(stage == CacheInitializationStage.FULL || stage == CacheInitializationStage.BEFORE_TRACK)
    	return super.getCacheState(cman, treeDB, stage);
      return CacheManipulatorInitializationStage.NON_APPLIABLE;
    }

    public void cacheRemove(Path iFile, long session) {
      Map<Path, UUID> rr = resourceToNode;
      if (rr.containsKey(iFile)) {;
        UUID uid = resourceToNode.remove(iFile);
        if(uid != null){
          rs.getUCM().removeUUID(uid,session);
          rresourceToNode.remove(uid);
        }
          //changed = true;
        //changed = true;
      }
    }

    public boolean inCache(Path relativePath) {
      rl.lock();
      boolean b = false;
      try{
        b = resourceToNode.get(relativePath) != null;
      }finally{
        rl.unlock();
      }
      return b;
    }

    public boolean inCache(UUID uuid) {
      rl.lock();
      try{
        boolean b = rresourceToNode.get(uuid) != null;
        return b;
      }finally{
        rl.unlock();
      }
    }

    public void cachePut(Path fullPath, Path relatedPath, UUID uuid, boolean save, long session) {
//      if (lock) {
//        wl.lock();
//      }
      try{
        if(fullPath == null)
          fullPath = getFullPath(relatedPath);//removed getCannonicali
        if(fullPath == null) {
          RLogger.logDebug("File null for "+relatedPath+" on "+ uuid);
          rs.getUCM().removeUUID(uuid,session);
          return;
        }
        if(!Files.exists(fullPath)) {
          RLogger.logDebug("attempt to track non-existing node "+fullPath+" in cache",1);
        }
        Path storedRelatedPath = null;
        if(resourceToNode!=null) {
          resourceToNode.put(relatedPath, uuid);
          storedRelatedPath = rresourceToNode.put(uuid, relatedPath);
        }
        Path oldFullPath = getFullPath(storedRelatedPath);
        boolean changed = oldFullPath == null || !oldFullPath.toAbsolutePath().equals(fullPath.toAbsolutePath());//TODO mb cannonical
        if(save){
          String oldPath = rs.getUCM().getPath(uuid,session);
          Path old = oldPath == null ? null : Path.of(oldPath);
          changed = false;
          
          Path spath = rs.getProjectRelativePath(fullPath);
          if(spath!=null && (old == null || !spath.equals(old)))
            rs.getUCM().setPath(uuid, spath.toString(), session);//TODO check

        }
        else 
          changed = true;//on init we cannot getRoot, so save later
        if(changed && save)
          rs.getUCM().setPath(uuid, relatedPath.toString(), session);//TODO check
      }
      finally{
//        if (lock&&wl!=null) wl.unlock();
      }
    }

    private Path getFullPath(Path relPath) {
      if(relPath == null)
        return null;
      return rs.getFullPath(relPath);
    }

    public UUID cacheGet(Path iFile) throws LockIsBusy {
      return cacheGet(iFile, LockType.LOCK_WAIT);
    }
    public UUID cacheGet(Path iFile, LockType lock) throws LockIsBusy {
      if(lock==LockType.LOCK_WAIT)
        rl.lock();
      if(lock == LockType.TRY_LOCK)
        if(!rl.tryLock())
          throw new LockIsBusy("Lock cannot be get");
      try{
        if(resourceToNode == null || iFile == null){
          RLogger.logError("map is not inited or file is null in cacheGet" );
          return null;
        }
        UUID uid = resourceToNode.get(iFile);
        return uid;
      }finally{
        if(lock!=LockType.NO_LOCK)
          rl.unlock();
      }
    }

    public void cacheReplace(Path oldFile, Path newFile, UUID uuid) {
      if (inCache(uuid)) ;
      {
        
        wl.lock();
        try{
          resourceToNode.remove(oldFile);
          resourceToNode.put(newFile, uuid);
          rresourceToNode.put(uuid, newFile);
          rs.getUCM().removeUUID(uuid, -1);//TODO check
          rs.getUCM().setPath(uuid, newFile.toString(),-1);//TODO check
        }
        catch(Exception e){
          RLogger.logError(e);
        }finally{
          wl.unlock();
        }
      }
    }

    public void updatePath(Path oldParentLocation, Path newParentLocation) {
      
      wl.lock();
      try{
        ArrayList<Path> resources = new ArrayList<>(resourceToNode.keySet());
        for (Path res : resources) {
          Path curLocation = res;
          //TODO test
          if (curLocation.startsWith((oldParentLocation+"/"))) {
            Path newLocation = null;
            if (newParentLocation != null) {
              newLocation = newParentLocation.resolve(
            		  oldParentLocation.relativize(curLocation)); 
            }
            UUID uuid = resourceToNode.remove(res);
            rs.getUCM().removeUUID(uuid, -1);
            if (newLocation != null) {
              rresourceToNode.put(uuid, newLocation);
              resourceToNode.put(newLocation, uuid);
              rs.getUCM().setPath(uuid, newLocation.toString(),-1);
            } else rresourceToNode.remove(uuid);
            
          }
        }
      }
      finally{
        wl.unlock();
      }
    }

    public Path getResourcePath(UUID uuid) {
      try {
        return getResourcePath(uuid, LockType.LOCK_WAIT);
      } catch (LockIsBusy e) {
        RLogger.logDebug(CoreUtils.getTrace(e));
      }
      return null;
    }
    public Path getResourcePath(UUID uuid,LockType lock) throws LockIsBusy {
      if(lock==LockType.LOCK_WAIT)
        rl.lock();
      if(lock == LockType.TRY_LOCK)
        if(!rl.tryLock())
          throw new LockIsBusy("Lock cannot be get");
      try{
        Path f = rresourceToNode==null?null:rresourceToNode.get(uuid);
        if (f == null)
          return null;
        return f;
      }
      finally{
        if(lock!=LockType.NO_LOCK)
          rl.unlock();
      }
      //return null;
    }
    private boolean initCaches(Path c, IUUIDsCachedTreeStorage rs, boolean lock, long session, IProgressMonitor mon) {
      return initCaches(null, c, rs, lock, session, mon);
    }
    private boolean initCaches(Path path, Path p, IUUIDsCachedTreeStorage rs, boolean lock, long session, IProgressMonitor mon) {
      state = 0;
      Map<Path, Pair<Path, UUID>> uids = new HashMap<>();
      Map<Path, Path> paths = new HashMap<>();
      List<Path> jsons = new LinkedList<>();
      p = p.normalize();
      if(mon.isCanceled()) return false;
      if(!listJsons(this, path, p, jsons, paths, mon))
        return false;
      if(mon.isCanceled()) return false;
      collectUUIDs(session, jsons, paths, uids, mon);
      if(stopped || mon.isCanceled())
        return false;
      Set<Path> vals = new HashSet<>(paths.values());
      vals.removeAll(uids.keySet());
      if(paths.size()!=uids.size()){
        RLogger.logError("Number of processed uuids is not equal to number of potential paths!");
      }
      state = 1;
      if(stopped || mon.isCanceled())
        return false;
      for(Entry<Path, Pair<Path, UUID>> s:uids.entrySet())
        cachePut(s.getValue().getFirst(), s.getKey(), s.getValue().getSecond(), true, state);
      state = 2;
      if(mon.isCanceled()) return false;
      return true;
      
    }
    private void collectUUIDFromPath(Path p, Map<Path, Pair<Path,UUID>> uids, Map<Path, Path> paths, long session){
      int s = uids.size();
      Path fpath = paths.get(p);
      try{
        if (!rs.fileAccessible(fpath)){ 
          RLogger.logError("Cannot access file! "+p.toAbsolutePath() );
          return;
        }
        UUID fuid = rs.getStorageUUId(fpath, legacyMode);
        if (fuid != null) {
          synchronized (uids) {
            Pair<Path, UUID> added = uids.put(fpath, new Pair<>(p, fuid));
            if(added!=null)
              RLogger.logError("Duplicate uuids! "+fpath +" "+fuid+" "+added.getSecond());
            if(uids.size()==s)
              RLogger.logError("UUID was not added!" );
          }
        } else {
          RLogger.logDebug("ERROR: File has no uid! " + p,7);
        }
      }finally{
        //System.out.println("Iter! "+i.get()+" "+(uids.size()-s) ); 
        if(uids.size()==s)
          System.out.println("ERROR some not added" ); 
      }
    }
    private void collectUUIDs(long session, List<Path> jsons, Map<Path, Path> paths, Map<Path, Pair<Path,UUID>> uids, IProgressMonitor mon) {
      //final long session = rs.getCacheStorage().getTransactionSession(); 

      List<Callable<Object>> tasks = jsons.stream().parallel().map(pa->{
        Callable<Object> c = new Callable<Object>() {

          @Override
          public Object call() throws Exception {
            try{
              collectUUIDFromPath(pa, uids, paths, session);
            }catch(Exception e){
              RLogger.logError(e);
            }finally{
            }
            return "done";
          }
        };
        return c;
      }).collect(Collectors.toList());
      try {
        List<Future<Object>> futs = pool.invokeAll(tasks);
        int ready = 0; 
        while(ready<futs.size()) {
          ready = 0;
          for(Future<Object> s: futs) {
            if(s.isDone() || s.isCancelled())
              ready++;
          }
          Thread.currentThread().join(200);
        }
      } catch (InterruptedException e) {
        RLogger.logError("Smth wrong - uuids gathering exception" );
        RLogger.logError(e);
      } 
    }

    private static boolean listJsons(ResourceMap rm, Path rpath, Path d,List<Path> jsonPaths, Map<Path, Path> jsonsMap, IProgressMonitor mon){
      LinkedList<Path> toh = new LinkedList<>();
      LinkedList<Path> tohp = new LinkedList<>();
      Set<Path> handled = new  HashSet<>();
      toh.add(d);
      tohp.add(rpath);
      while(!toh.isEmpty()){
        if(mon.isCanceled()) return false;
        if(rm.stopped)
          return false;
        Path p = toh.pollFirst();
        Path path = tohp.pollFirst();
        if(handled.contains(p.toAbsolutePath()))
          continue;
        handled.add(p.toAbsolutePath());
        if(Files.isDirectory(p)){
          DirectoryStream<Path> files = null;
          try {
            files = Files.newDirectoryStream(p);
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          try{
            if(files == null)
              RLogger.logError("empty folder "+p.toAbsolutePath() );
            else
            {
              Object[] a = StreamSupport.stream(files.spliterator(),false).toArray();
              if(mon.isCanceled()) return false;
              Arrays.stream(a).filter(
                pa->((Path)pa).toString().toLowerCase().endsWith(".json")
                ).forEach(
                  pas->{
                    if(mon.isCanceled()) return;
                    Path pat = (Path) pas;
                    
                    String fname = CoreUtils.getFileName(pat, true);
                   
                    Path jsonpath = path == null ? Path.of(fname): path.resolve(fname);
                    
                    jsonsMap.put(pat, jsonpath);
                    
                    fname = fname.substring(0,fname.length()-5);
                    if(!fname.isEmpty()) {
                      Path chi = p.resolve(fname);
                      if(Files.exists(chi) && !handled.contains(chi.toAbsolutePath())){
                        toh.addLast(chi);
                        tohp.addLast(path == null ? Path.of(fname) : path.resolve(fname));
                      }
                    }
                    jsonPaths.add(pat);
                  });
            }
            if(mon.isCanceled()) return false;
          }
          finally{
            try {
              if(files!=null)
                files.close();
            } catch (IOException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
          }
        }
      }
      return true;
    }

    public void initRoot() {
      
      writeLock();
      try{
        Path path = Path.of("root.json");
        if(rs.fileAccessible(path)){
          UUID uid = rs.getUUId(path, false);
          cachePut(getFullPath(path), path, uid, false, -1);
          rs.setRootNodeUUID(uid);
        }
      }
      finally{
        writeUnlock();
      }
    }

    private void loadUUIDs(Path p, IProgressMonitor mon) {
      if(rs.getRootNode() == null)
        return;
       rs.getUCM().forEachRecord(one->{
         if(mon.isCanceled()) return;
         String key = one.getFirst();
         Object val = one.getSecond();
         if(val == null)
           RLogger.logInfo("null value for " + key + " on " + rs.getVisibleName());
         UUID uid = UUID.fromString(key);
         String path = val!=null?val.toString():null;//TODO test
         //rs.fileAccessible(path) check removed
         cachePut(null, Path.of(path), uid, false, -1);
       });
    }

    public boolean saveUUIDs(boolean lock) {
      if(rs.getRootNode() == null)
        return false;
      if (lock) wl.lock();
      CacheStorage cs = rs.getCacheStorage(); 
      CacheTransactionDescription su = cs.startCacheTransaction("save uuids "+rs.getVisibleName(), CacheStorage.COMMIT_ALL,true);
      try{
        Map<UUID, Object> uids = new HashMap<>();
        for (Entry<UUID, Path> p : rresourceToNode.entrySet()) {
          uids.put(p.getKey(), p.getValue());
        }
        rs.getUCM().setUUIDs(rs.getVisibleName(), uids, -1);
        cs.getPSCM().setProjectSetting("_uuidsInited", "true");
      }finally{
        if (lock&&wl!=null) wl.unlock();
        if(su!=null && su.error == null)
          cs.commit(su);
      }
      return true;
    }

    public void readLock() {
      rl.lock();
    }

    public void readUnlock() {
      rl.unlock();
    }
    
    @Override
    public boolean isCrossProjectCache() {
      return false;
    }

    @Override
    protected OperationResult innerDropCache(Path db, SettingsManipulator cman) {
      wl.lock();
      try{
        if(resourceToNode!=null){
          resourceToNode.clear();
          rresourceToNode.clear();
        }
        rs.getUCM().dropAll();//TODO is it needed?
        return OperationResult.SUCEEDED;
      }finally{
        wl.unlock();
      }
    }
    @Override
    public OperationResult innerInitNotFirst(Path db, SettingsManipulator cman, IProgressMonitor mon) {
      wl.lock();
      try{
        rresourceToNode = new HashMap<>();
        resourceToNode = new HashMap<>();
        initRoot();
        loadUUIDs(db, mon);
        return OperationResult.SUCEEDED;
      }finally{
        wl.unlock();
      }
    }

    @Override
    protected OperationResult innerInitFirst(Path p, SettingsManipulator cman, IProgressMonitor mon) {
      //wl.lock();
      try{
        rresourceToNode = new HashMap<>();
        resourceToNode = new HashMap<>();
        initRoot();
        CacheTransactionDescription stt = null;
        CacheStorage cs = rs.getCacheStorage();
        if(mon.isCanceled()) return OperationResult.UNSUCEEDED;
        stt = cs.startForcedCacheTransaction("init uuids",CacheStorage.COMMIT_ALL, true);
        try{
          if(!initCaches(null, p, rs, false, stt.session, mon))
            return OperationResult.UNSUCEEDED;
          saveUUIDs(false);
        }
        catch(Exception e){
          RLogger.logError(e);
          if(stt != null && stt.error == null) {
            stt.error("already reverted");
            cs.rollback(stt);
          }
          throw e;
        }finally {
          if(stt != null && stt.error==null)
            cs.commit(stt);
        }
      }
      catch(Exception r){
        RLogger.logError(r);
      }
      finally{
        //wl.unlock();
      }
      return OperationResult.SUCEEDED;
    }

    @Override
    public void close() {
      // TODO Auto-generated method stub
      
    }
    private static String[] S_NAMES = new String[]{PARAM_NAME};
    private static String[] S_DEFS = new String[]{"1.2.3"};
    @Override
    protected String[] getSettingsNames(Path db) {
      return S_NAMES;
    }

    @Override
    protected String[] getVersions() {
      return S_DEFS;
    }

    @Override
    public void stop() {
      stopped = true;
    }
    @Override
    public String getName() {
      return NAME;
    }

    @Override
    protected boolean isNonRevision() {
      return false;
    }
  }