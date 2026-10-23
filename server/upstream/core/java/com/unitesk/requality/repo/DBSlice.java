package com.unitesk.requality.repo;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.LinkResolver;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class DBSlice {
  private boolean readOnly = false;
  private boolean deleted = false;
  public static final DBSlice EMPTY = new DBSlice();
  static {
    EMPTY.readOnly = true;
  }
  //private Pair<String,TreeDB[]> sliceInfo = null;
  private CacheStorageManager cacheManager = null;
  private Map<String, RevisionInfo> mapSliceRevs = null;
  private String sliceId;
  private RevisionInfo sliceRev = null;
  private String[] tids;
  private Map<String, TreeDB> trees = null;
  private Map<TreeDB,RevisionInfo> oldTreeRevs = new ConcurrentHashMap<>();
  private Map<TreeDB,IResTracker> oldTreeTracker = new ConcurrentHashMap<>();
//  private Map<TreeDB,RevisionInfo> newTreeRevs = new ConcurrentHashMap<>();
//  private Map<TreeDB,IResTracker> newTreeTracker = new ConcurrentHashMap<>();
  private Map<TreeDB,DBSlice> oldTreeSlice = new ConcurrentHashMap<>();
  private Map<RepoManager, RevisionInfo> storedRev  = new ConcurrentHashMap<>();
  
  private Map<TreeDB, RevisionInfo> rid;
  
  private boolean inited = false;
  private RevisionInfo baseRevision;
  private TreeDB baseTree;
  private RepoManager baseRm;
  private TreeDB[] basedbs = new TreeDB[0];
  private IProgressMonitor baseMonitor;
  public Collection<TreeDB> getTreeDBs() {
    if(trees == null)
      try {
        initTreeDBs();
      } catch (Exception e) {
        RLogger.logError(e);
      }
    if(trees == null)
      return Collections.emptyList();
    Collection<TreeDB> ret = new LinkedList<>();
    for(TreeDB db:basedbs) {
      TreeDB dba = trees.get(db.getLocalName());
      if(dba!=null)
        ret.add(dba);
      else
        ret.add(db);
    }
    return ret;
  }
  public static DBSlice create(CacheStorageManager cacheManager, TreeDB[] dbs, TreeDB db, IProgressMonitor mon) throws Exception{
    return create(cacheManager, dbs, Global.getRepoManager(db)
          , db
          , TreeStateManager.getRevisionInfo(db)
          , mon);
  }
  public static DBSlice create(CacheStorageManager csm, TreeDB[] set, RepoManager rm, TreeDB dbr, RevisionInfo ri, IProgressMonitor mon) throws Exception {
    DBSlice slice = new DBSlice();
    RepoTracker rt = rm.getRepoTracker();
    try {
      slice.cacheManager = csm;
      slice.basedbs = set;
      slice.sliceId = null;
      slice.sliceRev  = null;
      slice.mapSliceRevs = new HashMap<>();

      slice.basedbs = set;
      slice.baseRm = rm;
      slice.baseTree = dbr;
      slice.baseRevision = ri;
      slice.baseMonitor = mon;
      slice.initSliceRev(set, rm, dbr, ri, mon);
      
      return slice;
    }finally {
    }
  }
  private void initTreeDBs() throws Exception {
    if(trees!=null) {
      //TODO check this version
      for(TreeDB db :trees.values())
        db.getTracker().getTreeStateManager().setIsLoaded(db.getLocalName(), db.getProjectName(), db, db.revision);
    }else {
      TreeDB[] set = basedbs;
      RepoManager rm = baseRm;
      TreeDB dbr = baseTree;
      RevisionInfo ri = baseRevision;
      IProgressMonitor mon = baseMonitor;
      if(ri!=null){
        boolean inited = initSlice(set, rm, dbr, ri, mon);//mapsliceinfos are inited here
      
        if(!inited || trees == null || trees.size() == 0)
          RLogger.logInfo("One of treeDBs was not inited for slice "+sliceId+"!");
      }
    }
  
  }
  public void disable(IProgressMonitor mon) {
    if(!inited || readOnly)
      return;
    for(Entry<TreeDB, RevisionInfo> enotr: oldTreeRevs.entrySet()) {
      TreeDB db = enotr.getKey();
      RevisionInfo newRev = enotr.getValue();
      if(newRev == RevisionInfo.NO_REV || newRev == RevisionInfo.NONE)
        newRev = null;
      if(db.revision!=newRev)
        db.getTracker().getTreeStateManager().setRevisionInfo(db, newRev);
    }
    for(Entry<TreeDB, DBSlice>sliceinfo : oldTreeSlice.entrySet()) {
      DBSlice dbs = sliceinfo.getValue();
      if(TreeStateManager.getSliceInfo(sliceinfo.getKey())!=dbs)
        TreeStateManager.setSliceInfo(sliceinfo.getKey(),
          dbs);
    }
    if(oldTreeTracker!=null)
      for(Entry<TreeDB, IResTracker> enotr: oldTreeTracker.entrySet()) {
        TreeDB db = enotr.getKey();
        IResTracker tracker = enotr.getValue();
        if(db.getTracker()!=tracker) {
//      if(!closedTracks.contains(db.getTracker())) {
//        closedTracks.add(db.getTracker());
//        CacheStorage closed = db.getTracker().getCPCacheStorage(db);
//        closed.close();//close cp storages
//      }
          db.getCacheStorage().close();//close treeDB storages
          db.setTracker(tracker);
          db.getStorage().renew();
        //.getTreeStateManager().setState(db.getLocalName(),db.getProjectName(), db, TreeState.NOT_LOADED);
        }
      }
//    for(Entry<RepoManager, RevisionInfo> en: storedRev.entrySet()) {
//      en.getKey().forgetRevisionTree(en.getValue());
//    }
    if(trees!=null)
      for(TreeDB db: trees.values())
        if(db.revision!=null)
         db.setDisposed();
//    storedRev.clear();
//    trees = null;
//    inited = false;
  }
  public void enable(IProgressMonitor mon) {
    if(inited || readOnly) 
      return;
    RLogger.logDebug("enable slice "+(sliceId!=null?sliceId:"NULL_ID_SLICE"));
//    for(Entry<TreeDB, RevisionInfo> enotr: oldTreeRevs.entrySet()) {
//      TreeDB db = enotr.getKey();
//      db.getTracker().getTreeStateManager().setRevisionInfo(db, sliceRev);
//    }
//    for(Entry<TreeDB, IResTracker> enotr: oldTreeTracker.entrySet()) {
//      TreeDB db = enotr.getKey();
//      IResTracker tracker = baseRm.getRepoTracker();
////      db.getTracker().getTreeStateManager().setState(db.getLocalName(), db.getProjectName(), db,
////        TreeState.LOADED_NO_CACHE);//TODO test?
//      db.setTracker(tracker);
//    }
    try {
      initTreeDBs();
    } catch (Exception e) {
      RLogger.logError(e);
    }
    //return back slice and revinfos
    if(trees!=null)//can be null for DBEmpty
      for(TreeDB db : trees.values()) {
        TreeStateManager.setSliceInfo(db,
          this);
        db.revision = sliceRev;
        db.setTracker(baseRm.getRepoTracker());
      }
    inited = true;
    if(trees!=null) {
      for(TreeDB db: trees.values())
        updateRefValues(db);//TODO more proper way
    }
  }
  public static void updateRefValues(TreeDB db) {
    if(db == null)
      return;
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(db.getRootNode(),false,false,true);
    Iterator<TreeNode> it = dftw.iterator();
    while(it.hasNext()) {
      TreeNode node = it.next();
      if(node!=null) {
        db.getLinkResolver();
        LinkResolver.resetLinksObject(node);
      }
    }
  }
  public boolean isDeleted() {
    return deleted;
  }
  public void delete() {
    if(deleted)
      return; //already deleted
    if(this.readOnly)
      throw new RuntimeException("Cannot delete static empty slice!");
    if(isEnabled()) {
      disable(baseMonitor);
      if(trees!=null)
        for(TreeDB db: trees.values()){
          if(db == null)
            continue;
          RevisionInfo ri = TreeStateManager.getRevisionInfo(db);
          if(ri!=null && ri.isSingleRev())
          {
            TreeDB rootDB = Instantiator.getRootTree(db);
            if(rootDB == db) {
              for(TreeDB dba : basedbs)
                if(dba.getLocalName().equals(db.getLocalName())) {
                  rootDB = dba;
                  break;
                }
            }
            RepoManager rma = Global.getRepoManager(rootDB);
            if(rma!=null)
              rma.forgetRevisionTree(ri);
          }
        }
      trees = null;
    }
    deleted = true;
    readOnly = false;
    
    cacheManager.unload(sliceId);
    sliceId = null;
    sliceRev = null;

    cacheManager = null;
    mapSliceRevs = null;
    sliceRev = null;
    trees = null;
    oldTreeRevs = null;
    oldTreeTracker = null;
    oldTreeSlice = null;
    storedRev = null;
    rid = null;
    baseTree = null;
    baseRevision = null;
    basedbs = null;
    //sliceInfo = null;
  }

  Map<String, RevisionInfo> getMapSliceRevs(){
    return mapSliceRevs;
  }
//  Map<String,TreeDB> getMapSlice(){
//    return mapSliceInfo;
//  }
  private String treeId(String resname, RevisionInfo ri){
    return treeId(resname,ri!=null?ri.getRevision():null);
  }
  private String treeId(String resname, String revId) {
    if(revId!=null)
       return resname+"-"+revId;
    return resname;
  }
  private String treeId(TreeDB db){
    return treeId(db, 
            TreeStateManager.getOne(db.getTracker()).getRevisionInfo(db));
  }
  private String treeId(TreeDB db, RevisionInfo ri){
    if(ri!=null)
      return db.getProjectName()+"-"+ri.getRevision();
    return db.getProjectName();
  }
  private String treeId(TreeDB db, String ri){
    return treeId(db.getProjectName(), ri);
  }
  private boolean initSliceRev(TreeDB[] set, RepoManager rm, TreeDB db, RevisionInfo ri, IProgressMonitor mon) {
    String pname = db.getProjectName();
    String lname = db.getLocalName();
    String tid = treeId(pname, ri);
    if(sliceId!=null && sliceId.contains(pname))//TODO mb change back to tid
      return true;
    rid = new HashMap<>();
    if(ri instanceof SliceRevisonInfo){
      sliceRev = ri;
      sliceId = sliceRev.getRevision();
      rid = ((SliceRevisonInfo) sliceRev).getDBRevisions();
      //TODO proper?
      for(TreeDB dba : set) {
        if(!oldTreeRevs.containsKey(dba))
          oldTreeRevs.put(dba, dba.revision==null?RevisionInfo.NO_REV:dba.revision);
        if(!oldTreeTracker.containsKey(dba))
          oldTreeTracker.put(dba, dba.getTracker());
        if(!oldTreeSlice.containsKey(dba))
          oldTreeSlice.put(dba, TreeStateManager.getSliceInfo(dba));

        mapSliceRevs = new HashMap<>();
        if(mapSliceRevs!=null)
          mapSliceRevs.put(dba.getLocalName(), rid.get(dba));
      }
      return true;
    }
    
    if(set != null)
    {
      for(TreeDB dba : set) {
        //TODO not to store?
        if(!oldTreeRevs.containsKey(dba))
          oldTreeRevs.put(dba, dba.revision==null?RevisionInfo.NO_REV:dba.revision);
        if(!oldTreeTracker.containsKey(dba))
          oldTreeTracker.put(dba, dba.getTracker());
        if(!oldTreeSlice.containsKey(dba))
          oldTreeSlice.put(dba, TreeStateManager.getSliceInfo(dba));
      }
      tids = new String[set.length];
      int i = 0;
      HashMap<TreeDB, RevisionInfo> riMap = new HashMap<>();
      mon.beginTask("Init slice for "+tids.length+" trees. Load repo data", tids.length);
      try {
        for(int dbi = 0; dbi < tids.length; dbi++)
        {
          TreeDB dba = set[dbi];
          RevisionInfo ria = null;
          if(dba.getProjectName().equals(pname)){
            tids[i] = tid;
            ria = ri;
            riMap.put(dba, ri);
          }
          else{
            RepoManager rma = Global.getRepoManager(dba);
            if(rma!=null && ri!=null){
              try {
                ria = rma.getRevisionInfoByRev(ri.getRevision(dba), mon);
              } catch (Exception e) {
              }
              if(ria == null)
                ria = rma.getRevisionInfoByDate(ri.getDate());
              if(ria == null || ria == RevisionInfo.NO_REV || ria == RevisionInfo.NONE)
                tids[i] = treeId(dba, "NONE");
            }else
              tids[i] = treeId(dba);
            if(ria != null)
              tids[i] = treeId(dba.getProjectName(), ria);
            riMap.put(dba, ria);
          }
          rid.put(dba, ria);
          if(mapSliceRevs!=null)
            mapSliceRevs.put(dba.getLocalName(), ria);
          i++;
          mon.worked(1);
        }
      }finally {
        mon.doneTask();
      }
//      Arrays.sort(tids);
//      sliceId = String.join("-",tids);
//      Arrays.sort(tids);
      sliceRev = new SliceRevisonInfo(ri, riMap);
      sliceId = sliceRev.getRevision();
    }else
      return false;
    return true;
  }
  private boolean initSlice(TreeDB[] set, RepoManager rm, TreeDB db, RevisionInfo ri, IProgressMonitor mon) throws Exception{
    if(readOnly)
      throw new RuntimeException("Cannot init read-only slice!");
    try {
      String pname = db.getProjectName();
      String lname = db.getLocalName();
      String tid = treeId(pname, ri);
      if(set != null)
      {  
        RepoTracker rt = rm.getRepoTracker(); 
        int i = 0;
        Map<CacheStorage, CacheTransactionDescription> initedTrans = new HashMap<>();
        for(TreeDB dba: set){
          //seems not so usefull because of instance change for revisions
        }
        try {
          //first we set ids then load all trees
          if(trees == null) {
            trees = new HashMap<>();
            for(TreeDB basedb:set){
              mon.beginTask("Slice init - start to load "+basedb.getSemiLocalName(), 1);
              try {
                TreeStateManager tsm = basedb.getTracker().getTreeStateManager();
                RevisionInfo rida = rid.get(basedb);
                TreeDB sliced = null;
                RevisionInfo ribase = TreeStateManager.getRevisionInfo(basedb);
                {
                  RepoManager rma = Global.getRepoManager(basedb);
                  sliced = basedb;
                  if(rma!=null){
                    if(rida == null || isNone(rida))
                      sliced = null;
                    else if(ribase == rida)
                      sliced = basedb;
                    else 
                    {
                      mon.doneTask();
                      mon.beginTask("Slice init - load revision for "+basedb.getSemiLocalName(), 1);
                      Pair<RevisionInfo, TreeDB> revt = rma.getRevisionTree(sliceRev, true, false, true, mon);
                      //not init cache for here, init after that on enabled
                      sliced = null;
                      if(revt!=null && revt.getSecond()!=null) {
                        sliced = revt.getSecond();
                        RevisionInfo ria = revt.getFirst();
                        if(ria == null)
                          ria = RevisionInfo.NO_REV;
                        storedRev.put(rma, ria);
                      }
                      if(sliced == null) {
                        sliced = rma.getRevisionTree(sliceRev, true, false, true, mon).getSecond();
                        RLogger.logError("Incorrect db state for tree "+pname+" on revision"+ri);
                      }
                    }
                  }
                }
                if(sliced == null) {
                  //no that tree for specific revision
                  //TODO change beh for it?
                }
                else
                  trees.put(sliced.getLocalName(), sliced);
              }finally {
                mon.doneTask();
              }
            }
          }
          for(TreeDB dba:trees.values())
          {
            CacheStorage cs = dba.getTracker().getCacheStorage(dba);
            if(!initedTrans.containsKey(cs))
              initedTrans.put(cs, cs.startCacheTransaction("Cp cache operations on "+dba.getProjectName()+" during slice init", 2));
          }

          for(TreeDB dba: set) {
            String dblname = dba.getLocalName();
            TreeDB slice = trees.get(dblname);
            if(slice!=null) {
              CacheStorage cs = slice.getCacheStorage();
              CacheTransactionDescription ctd = cs.startCacheTransaction("Initiate slice caches ", 2);
              try {
                slice.getCMM().getLinkManipulator().initNames(slice, mon);//preinit names
              }finally {
                if(ctd!=null && ctd.error == null)
                  cs.commit(ctd);
              }
            }else
              RLogger.logDebug("Tree "+dblname+" was not found for slice "+(sliceId!=null?getSliceId():(" on tree "+pname)));
          }
          for(TreeDB dba: set) {
            String dblname = dba.getLocalName();
            TreeDB slice = trees.get(dblname);
            if(slice!=null) {
              CacheStorage cs = slice.getCacheStorage();
              CacheTransactionDescription ctd = cs.startCacheTransaction("Initiate slice caches ", 2);
              try {
                slice.initCaches(dblname, slice.getProjectName(), slice.revision, true);
              }finally {
                if(ctd!=null && ctd.error == null)
                  cs.commit(ctd);
              }
            }else
              RLogger.logDebug("Tree "+dblname+" was not found for slice "+(sliceId!=null?getSliceId():(" on tree "+pname)));
          }
        } finally {
          //TODO load parallel and commit here changes
          for(Entry<CacheStorage, CacheTransactionDescription> en:initedTrans.entrySet()){
            if(en.getValue() != null && en.getValue().error == null)
              en.getKey().commit(en.getValue());
          }
        }
        return true;
      }
    }catch(Exception e) {
      e.printStackTrace();
    }finally {
    }
    return false;
    //return new Pair<>("cpNotInited", new TreeDB[0]);
  }
  private boolean isNone(RevisionInfo ribase) {
    return ribase == null || ribase.getRevision() == null || ribase.getRevision().equals("NONE");
  }
  public String getSliceId() {
    return sliceId;
  }

  public TreeDB innerGetTreeDB(TreeDB dbFrom, String lname, String pname, boolean load, boolean loadCache) {
    if(trees == null) {
      RLogger.logError("Tries to get a tree "+(lname!=null?lname:pname)+" from uninited slice "+sliceId);
      return null;
    }
    TreeDB db = trees.get(lname);
    if(db == null)
      for(TreeDB dba:trees.values())
        if(dba.getProjectName().equals(pname))
          return dba;
    return db;
  }
  public TreeDB getTreeDB(TreeDB dbFrom, String lname, String pname, boolean load, boolean loadCache) {
    if(!inited)
      return null;
    return innerGetTreeDB(dbFrom, lname, pname, load, loadCache); 
  }
  public RevisionInfo getRevisionInfo() {
    return sliceRev;
  }
  public boolean isEnabled() {
   return trees != null;
  }
  public TreeDB getStoredTree(String localName) {
    if(trees == null || localName == null)
      return null;
    return trees.get(localName);
  }
}
