package com.unitesk.requality.core.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.api.IStoppable;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.reference.TreeRelationsCounter;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.cache.CacheManipulator;
import com.unitesk.requality.core.storage.cache.CacheManipulatorInitializationStage;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.storage.cache.ICacheStorageProvider;
import com.unitesk.requality.core.storage.cache.LinksCacheMan;
import com.unitesk.requality.core.storage.cache.NamesCacheMan;
import com.unitesk.requality.core.storage.cache.RelationRecord;
import com.unitesk.requality.core.storage.cache.SettingsManipulator;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public class CPLinkResolver extends CacheManipulator<TreeDB> implements IStoppable{
  public static String NAME="Cross-project links resolver";
  public static String REV_INITED="_revInitedCP";
  ReentrantReadWriteLock rrwl = new ReentrantReadWriteLock();

  IResTracker irt = null;
  ICacheStorageProvider cp = null;
  private boolean stopped = false;
  private LinksCacheMan lcm = null;
  private NamesCacheMan ancm = null;
  private static Map<String, Object> lateLoadingLocks = new ConcurrentHashMap<>();
  //private static Map<RequalityElement, Document> proxy = new HashMap<>();
  
  public Pair<TreeRelationsCounter, TreeRelationsCounter> getRelatedProjects(CacheStorage cs, String lname, String pname){
    return getLCM().getRelatedProject(cs, lname, pname);
  }

  public Pair<TreeRelationsCounter, TreeRelationsCounter> getLateRelatedProjects(CacheStorage cs, String lname, String pname, Map<String, List<LateResolveRequest>> map){
    return getLCM().getLateRelatedProject(cs, lname, pname, map);
  }
  //private Map<String, List<LateResolveRequest>> alates = null;

  @Override
  public CacheManipulatorInitializationStage getCacheState(SettingsManipulator sman, TreeDB treeDB, CacheInitializationStage stage) {
    if(stage == CacheInitializationStage.FULL || stage == CacheInitializationStage.INIT)
      return super.getCacheState(sman, treeDB, stage);
    return CacheManipulatorInitializationStage.READY;
  }
  private IStateChangeHandler readyHook = new AAllTreeStateChangeHandler() {
    @Override
    public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState, TreeState state) {
      fulfillLatesRequests(db, lname, pname, null);
    }
  };
  private static Map<TreeStateManager, IStateChangeHandler> cpReadyHook = new HashMap<>();
  public CPLinkResolver(IResTracker rt) {
    this.irt = rt;
    this.cp = rt;
    TreeStateManager tsm = rt.getTreeStateManager();
    synchronized (cpReadyHook) {
      IStateChangeHandler handler = cpReadyHook.get(tsm);
      if(handler == null) {
        cpReadyHook.put(tsm, readyHook);
        TreeState[]states = new TreeState[] {TreeState.READY};
        tsm.addStateHook("*", 
                states, readyHook);
      }
    }
    //update lates when treedb is fully loaded
    //if (db != null) db.addListener(changeListener);
  }

  private IResTracker getTracker(){
    if(irt == null)
      if(cp!=null && cp instanceof TreeDB)
        irt = ((TreeDB)cp).getTracker();
    return irt;
  }

  public LinksCacheMan getLCM(){
    if(lcm==null)
        lcm = new LinksCacheMan(cp, true);
    return lcm;
  }
  protected NamesCacheMan getNCM(){
    if(ancm == null)
        ancm = new NamesCacheMan(cp, true);
    return ancm;
  }
    
  public void close() {
  }


  public Map<String, List<LateResolveRequest>> getLatesRelationsRecords(CacheStorage cs){
    return getLatesRelationsRecords(this, 
          cs);
  }
  public Map<String, List<LateResolveRequest>> getRevLatesRelationsRecords(CacheStorage cs){
    return getRevLatesRelationsRecords(this, 
          cs);
  }
  public Map<String, List<LateResolveRequest>> getLatesRelationsRecords(TreeDB db){
    return getLatesRelationsRecords(db.getTracker().getCPLinkResolver(db), 
          db.getTracker().getCPCacheStorage(db));
  }
  public Map<String, List<LateResolveRequest>> getRevLatesRelationsRecords(TreeDB db){
    return getRevLatesRelationsRecords(db.getTracker().getCPLinkResolver(db), 
          db.getTracker().getCPCacheStorage(db));
  }
  public Map<String, List<LateResolveRequest>> getRevLatesRelationsRecords(CPLinkResolver lr, CacheStorage cs){
    Map<String, List<LateResolveRequest>> alates = null;
    if(getTracker()!=null){
      boolean isEmpt = false;
      synchronized (this) {
        isEmpt = alates == null;
      }
      if(isEmpt){
        Map<String, List<LateResolveRequest>> lCache = lr.loadRevLatesRelationsFromCache(cs);
        synchronized (this) {
          if(alates == null) {
            if(lCache == null || lCache.isEmpty())
              alates = new HashMap<>();
            else
              alates = lCache;
          }
        }
      }
      return alates;
    }
    return null;
  }
  public Map<String, List<LateResolveRequest>> getLatesRelationsRecords(CPLinkResolver lr, CacheStorage cs){
    Map<String, List<LateResolveRequest>> alates = null;
    if(getTracker()!=null){
      boolean isEmpt = false;
      synchronized (this) {
        isEmpt = alates == null;
      }
      if(isEmpt){
        Map<String, List<LateResolveRequest>> lCache = lr.loadLatesRelationsFromCache(cs);
        synchronized (this) {
          if(alates == null) {
            if(lCache == null || lCache.isEmpty())
              alates = new HashMap<>();
            else
              alates = lCache;
          }
        }
      }
      return alates;
    }
    return null;
  }
  private void registerLateResolve(CacheStorage cs, TreeDB db, String name, TreeNode node, String key) {
    if(db== null && node!=null)
      db = node.getTreeDB();
    if(db!=null && node!=null)
      getLCM().addLateRecord(cs, db.getLocalName(),
        db.getProjectName(), name, node.getUUId().toString(), key);
    //saveLates();
  }

  public Map<String, List<LateResolveRequest>> loadLatesRelationsFromCache(CacheStorage cf) {
    return getLCM().loadLates(cf);
  }
  public Map<String, List<LateResolveRequest>> loadRevLatesRelationsFromCache(CacheStorage cf) {
    return getLCM().loadRevLates(cf);
  }
//  public Map<String, List<LateResolveRequest>> loadLatesRelationsFromCache(TreeDB db) {
//    return getLCM().loadLates(db);
//  }

  public void saveLatesRelationsToCache(TreeDB db, Map<String, List<LateResolveRequest>> map) {
    getLCM().saveLates(db, map);
    //alates = null; 
  }
  public void saveLatesRelationsToCache(CacheStorage cs, Map<String, List<LateResolveRequest>> map) {
    getLCM().saveLates(cs, map);
    //alates = null;
  }
  public void saveRevLatesRelationsToCache(String lname, String pname, TreeDB db, Map<String, List<LateResolveRequest>> map) {
    getLCM().saveRevLates(lname, pname, db, map);
    //alates = null; 
  }
  public void saveRevLatesRelationsToCache(String lname, String pname, CacheStorage cs, Map<String, List<LateResolveRequest>> map) {
    getLCM().saveRevLates(lname, pname, cs, map);
    //alates = null;
  }
//
//  public ReferenceValue[] getRefValuesByAttributeName(TreeNode node, String key) {
//    if (node == null) return new ReferenceValue[]{new ReferenceValue(key, "Source not found!")};
//    return getRefValuesByAttributeName(node, key, null);
//    //    TreeNode node = attrs().getLinkAttributeValue(key);
//    //    return node==null?"Missing reference: '"+attrs().getStringAttributeValue(key)+"'":node.getUserVisibleName(false);
//  }
//
//  public static ReferenceValue[] getRefValuesByAttributeName(TreeNode targ, String key, String label) {
////    List<TreeNode> nde =
////        targ.getTreeDB()
////            .getLinkResolver()
////            .getStorage()
////            .resolveLink(targ.getTreeDB().getLinkResolver(), targ, key, null, label, true, true);
//    Attribute a = targ.attrs().getAttribute(key);
//    if (a!=null){
//      Object val = a.getValue();
//      if(val!=null)
//      {
//        if(a.getType()==AttributeType.REFERENCE)
//        {
//          ReferenceValue v = (ReferenceValue) val;
//          if(v.hasErrorsOnResolve(targ.attrs()))
//            return new ReferenceValue[]{v};
//          v.getNodes(targ.attrs());
//          return new ReferenceValue[]{v};
////            ret.add(node);
////          return ret.toArray(new Object[0]);
//        }else
//        {
//          List<ReferenceValue> ret = new LinkedList<>();
//          ListValue lv = (ListValue) a.getValue();
//          for(Object o : lv.getValues()){
//            if(o == null)
//              ret.add(new ReferenceValue(key, ReferenceValueProvider.NULL_MESSAGE.substring(1)));
//            else{
//              ReferenceValue v = (ReferenceValue) o;
//              if(v.hasErrorsOnResolve(targ.attrs()))
//                ret.add(v);
//              else
//                ret.add(v);
//            }
//          }
//          return ret.toArray(new ReferenceValue[0]);
//        }
//      }
//      else
//        return new ReferenceValue[]{new ReferenceValue(key, ReferenceValueProvider.NULL_MESSAGE)};
//    }
////    if (label == null) label = targ.attrs().getStringAttributeValue(key);
////    return (nde == null || nde.size() == 0)
////        ? ("Missing reference: '" + label + "'")
////        : nde.iterator().next().getUserVisibleName(false);
//    return new ReferenceValue[]{new ReferenceValue(key, ReferenceValueProvider.NULL_MESSAGE)};
//  }

  
  public Map<TreeNode, String> getLinksToNode(TreeNode node, String key, boolean validate) {
    return getLinksToNode(node, key, true, validate);
  }
  public Map<TreeNode, String> getLinksToNode(TreeNode node, String key, boolean cp, boolean validate) {
    List<RelationRecord> st = null;
    TreeDB dba = node.getTreeDB();
    if(cp)
    {
      CacheStorage cs = dba.getTracker().getCPCacheStorage(dba);
      cs.readLock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
      try{
        st = dba.getTracker().getCPLinkResolver(dba).getLCM().getCPRevLinks(dba,node.getUUId(), key); 
      }
      finally{
        cs.readUnlock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
      }
    }else
      st = getLCM().getRevLinks(node);
    Map<TreeNode, String> revs = null;
    //TODO optimize for cp
    if(st!=null)
    for (RelationRecord rr : st) {
      TreeDB db = dba,adb = db;
      if(rr.projectFrom!=null&&rr.isCP()){
        adb = db.getTracker().getTreeDB(db,null, rr.projectFrom, false, db.getMonitor());//TODO IDE monitor
        //key = key;//it is not use :(
      }
      TreeNode nodeFrom = null;
      try{
        if(adb!=null)
          nodeFrom = adb.getNodeExc(rr.nodeFrom);
      }catch(NodeNotFoundException nfe){
        RLogger.logDebug(nfe.toString() + CoreUtils.getTrace(nfe), 3);
      }
      if(nodeFrom != null && !nodeFrom.isDisposed()){
        Attribute a = nodeFrom.attrs().getAttribute(key);
        if (a != null && nodeFrom.attrs().isReference(key)) {
          boolean isvalid = !validate;
          String akey = key;
          boolean mult = false;
          int al = 0;
          ;
          if(validate){
            if (a.getType() == AttributeType.REFERENCE) {
              isvalid =
                  nodeFrom
                      .getTreeDB()
                      .getLinkResolver()
                      .testNodeByPath(nodeFrom, node, ((ReferenceValue)a.getValue()).getRaw().toString());
            } else {
              List<Object> list = ((ListValue) a.getValue()).getValues();
              int l = 0;
              mult = list.size() > 1;
              for (Object o2 : list) {
                if (nodeFrom
                    .getTreeDB()
                    .getLinkResolver()
                    .testNodeByPath(nodeFrom, node, ((ReferenceValue)o2).getRaw().toString())) {
                  isvalid = true;
                  al = l;
                }
                l++;
              }
            }
          }
          if (isvalid) {
            if(revs == null)
              revs = new HashMap<>();
            revs.put(nodeFrom, rr.nodeToVersion);
//              getStorage().addLink(node2, node, key, true);
//              getStorage().saveRevLinks(node);
          }
        }
      }
    }
    if(revs == null)
      return Collections.emptyMap();
    return revs;
  }
  public Map<String, Map<TreeNode, String>> getLinksToNode(TreeNode rnode, boolean cp) {
    return getLinksToNode(rnode, cp, false);
  }

  private void removeIncorrectLinks( TreeNode node) {
    List<RelationRecord> rev = getDirectLinksCacheItems(node);
    //rl.unlock();
    List<RelationRecord> todel = null;
    if (rev != null && rev.size() > 0) {
      for (RelationRecord rr: rev){
        if(rr.isCP()){
          CheckResult cr = checkLinkIsCorrect(node.getTreeDB(), rr);
          if (cr != CheckResult.OK) {
            if(todel == null)
              todel = new LinkedList<>();
            todel.add(rr);
          }
        }
      }
    }
    if(todel!=null){
      getLCM().updateCPLinks(node.getTreeDB(), todel, Collections.emptyList(), true);
    }
  }

  
  private CheckResult checkLinkIsCorrect(TreeDB db, RelationRecord rr) {
    TreeDB dbfrom = db,dbto = db;
    if(rr.isCP()){
      if(db.getSemiProjectName().equals(rr.projectFrom))
        dbto = db.getTracker().getTreeDB(db,null, rr.projectTo, false, db.getMonitor());//TODO IDE monitor
      else
        dbfrom = db.getTracker().getTreeDB(db,null, rr.projectFrom, false, db.getMonitor());//TODO IDE monitor
    }
    if(dbfrom == null || dbto == null)
      return dbfrom == null ? CheckResult.MISS_PROJ_FROM : CheckResult.MISS_PROJ_TO;
    TreeNode from = dbfrom.getNode(rr.nodeFrom),
             to = dbto.getNode(rr.nodeTo);
    if (from == null || from.isDisposed()) return CheckResult.MISS_NODE_FROM;
    if (to == null || to.isDisposed()) return CheckResult.MISS_NODE_TO;
    String savedName = rr.key;
    //TODO check []links to be used
    int ind = savedName.indexOf("["), pos = -1;
    if (ind > -1) {
      int end = savedName.indexOf("]", ind + 1);
      if (end > -1) {
        pos = Integer.parseInt(savedName.substring(ind + 1, end));
        savedName = savedName.substring(0, ind) + savedName.substring(end + 1);
      }
    }
    //if(from!=null){
    Attribute a = from.attrs().getAttribute(savedName);
    if (a == null || !a.isReference()) return CheckResult.WRONG_ATTR_TYPE;
    int i = 0;
    TypedValue tv = a.getTValue();
    if(tv.getType() == AttributeType.REFERENCE){
      ReferenceValue rv = tv.getGValue();
      if(rv.getStringValue()!=null && rv.getStringValue().startsWith(ReferenceValueProvider.ERROR_CHAR+"Missing") && dbto!=null)
        rv.resetAttribute(from.attrs(), tv.getType(), rv);
      UUID[] uids = rv.getNodeUUIDs(from.attrs());
      if(uids != null)
        for(UUID node:uids)
          if(node.equals(to.getUUId())||to.getCloneParentUUId()!=null && to.getCloneParentUUId().equals(node))
            return CheckResult.OK;//TODO think
    }else{
      ListValue lv = a.getGValue();
      boolean reset = false;
      for (Object s : lv.getValues()) {
        ReferenceValue rv = (ReferenceValue)s;
        if(rv.getStringValue()!=null && rv.getStringValue().startsWith(ReferenceValueProvider.ERROR_CHAR+"Missing") && dbto!=null)
          reset = true;
      }
      if(reset)
        ReferenceValue.resetAttribute(from.attrs(), tv.getType(), lv); 
      for (Object s : lv.getValues()) {
        ReferenceValue rv = (ReferenceValue)s;
        UUID[] uids = rv.getNodeUUIDs(from.attrs());
        if(uids!=null)
          for(UUID node:uids)
            if(node.equals(to.getUUId())||to.getCloneParentUUId()!=null && to.getCloneParentUUId().equals(node))
          return CheckResult.OK;
        i++;
      }
    }
    return CheckResult.NO_ATTR_VALUE;
  }
  public boolean registerLates(AttributeStorage where, String key, AttributeType type, Object value, Map<String, List<LateResolveRequest>> alates) {
    if(value == null)
      return false;
    if(type == AttributeType.REFERENCE){
      Object raw = null;
      if(value instanceof ReferenceValue)
      {
        ReferenceValue rv = (ReferenceValue)value;
        raw = rv.getRaw();
      }else
        raw = value.toString();
      if(raw == null)
        return false;
      String sref = raw.toString();
      int ind = sref.indexOf("::");
      if(ind>-1)
      {
        String dbname = sref.substring(0,ind);
        TreeDB wdb = where.getNode().getTreeDB();
        TreeDB db = wdb.getTracker().getTreeDB(wdb,null, dbname, false,wdb.getMonitor());
        //TODO test
        if(db == null || db == wdb || !db.getCMM().getLinkManipulator().getNamesInited(db)){
          CacheStorage cs = wdb.getTracker().getCPCacheStorage(wdb);
          synchronized(getLatesLockObject(wdb, dbname)) {
            //Map<String, List<LateResolveRequest>> map = getLatesRelationsRecords(wdb);
            List<LateResolveRequest> links = alates.getOrDefault(dbname, Collections.emptyList());
            if(!wasRegisteredLate(links, dbname, where.getNode(), key)){
              wdb.getTracker().getCPLinkResolver(wdb).registerLateResolve(
                  cs,
                  wdb, dbname, where.getNode(), key);
              if(!alates.containsKey(dbname))
                alates.put(dbname, new LinkedList<>());
              alates.get(dbname).add(new LateResolveRequest(where.getNode().getUUId(),wdb.getLocalName(), wdb.getProjectName(),key));
              return true;
            }
          }
        }
      }
      return false;
    }
    ListValue lv = (ListValue)value;
    boolean b = false;
    for(Object o:lv)
      b = registerLates(where, key, lv.getType(), o, alates) || b;
    return b;
  }
  private static boolean wasRegisteredLate(
      List<LateResolveRequest> links, String dbname, TreeNode node, String key) {
    for(LateResolveRequest lrr: links)
      if(lrr.key.equals(key) && (lrr.projName!=null && lrr.projName.equals(dbname)) && lrr.uid.equals(node.getUUId()))
        return true;
    return false;
  }

  public Set<String> handleLinksAttributesChanges(TreeNode node, Set<String> ofint,Map<String,Attribute>newa, Map<String, List<LateResolveRequest>> alates, byte flag){
    if (getLCM().getStorage(node.getTreeDB()) == null) 
      return Collections.emptySet();
    List<RelationRecord> toa = null,tod = null,cptoa = null,cptod = null;
    Set<String> ret = Collections.emptySet();
    String nid = node.getSUUId(),
      cpnid = getCPId(node);
    // linkname -> unneded ket -> ids(project_name::uuid or uuid) of links ends
    Map<String, List<RelationRecord>> vals = 
        getDirectLinksCacheItemsAsMapByAttrName(node, ofint);
    boolean latesChanges = false;
    for(String key:ofint){
      Set<Object> oldS = new HashSet<>();
      List<RelationRecord> old = null;
      if(vals.get(key)!=null)
        old = vals.get(key);
      Attribute a = newa.get(key);
      if (a != null && a.isReference() && a.isCP()) {
        AttributeType type = a.getType(); 
        Object val = a.getValue();
        if(!OFlag.isNoLates(flag))
          synchronized (LinksCacheMan.class) {
            latesChanges = registerLates(node.attrs(), key,type,val, alates) || latesChanges;
          }
        ReferenceValue.resetAttribute(node.attrs(), type, a.getValue());
        Map<TreeNode, String>list = ReferenceValue.getValuesMap(node.attrs(), type,a.getValue());
        if(list!=null)
          for(Entry<TreeNode, String> en:list.entrySet()){
            TreeNode nde = en.getKey();
            boolean cpr = isCPRelation(node, nde);
            if(!cpr && toa == null)
              toa = new LinkedList<>();
            if(cpr && cptoa == null)
              cptoa = new LinkedList<>();
            addValue(toa, cptoa, node, nde, key, cpr, en.getValue());
            if(ret.isEmpty())
              ret = new HashSet<>();
            ret.add(key);
          }
      }

      if(old != null){
        for(RelationRecord rr: old){
          boolean cpr = rr.isCP();
          if(!cpr && tod == null)
            tod = new LinkedList<>();
          if(cpr && cptod == null)
            cptod = new LinkedList<>();
          addValue(tod, cptod, rr);
          if(ret.isEmpty())
            ret = new HashSet<>();
          ret.add(key);//TODO check;
        }
      }
    }
    TreeDB dba = node.getTreeDB();
    CacheStorage cps = dba.getTracker().getCPCacheStorage(dba);
    boolean cpchanges = latesChanges || cptoa!=null || cptod!=null;
    CacheTransactionDescription tm = null;
    if(cpchanges)
      tm = cps.startCacheTransaction("changes cp links for "+node.getSUUId(), CacheStorage.COMMIT_ALL);
    try{
//      synchronized (LinksCacheMan.class) {
//        if(latesChanges) {
//          synchronized(getLatesLockObject(dba, dba)) {
//            CPLinkResolver lr = dba.getTracker().getCPLinkResolver(dba);
//            lr.saveLatesRelationsToCache(cps, alates==null?
//              getLatesRelationsRecords(lr, cps):
//                alates);
//          }
//        }
//      }
      if(cptoa!=null || cptod!=null){
        CPLinkResolver clr = dba.getTracker().getCPLinkResolver(dba);
        clr.getLCM().updateCPLinks(node.getTreeDB(), cptod!=null?cptod:Collections.emptyList(), cptoa!=null?cptoa:Collections.emptyList(), false);
      }
    }
    finally{
      if(tm != null && tm.error == null)
        cps.commit(tm);
    }
    return ret;
  }
  
  private Map<String, List<RelationRecord>> getDirectLinksCacheItemsAsMapByAttrName(TreeNode node, Set<String> ofint) {
    List<RelationRecord> list = getDirectLinksCacheItems(node, ofint);
    Map<String, List<RelationRecord>> dirMap = new TreeMap<>();
    for(RelationRecord r:list){
      if(!dirMap.containsKey(r.key))
        dirMap.put(r.key, new LinkedList<>());
      dirMap.get(r.key).add(r);
    }
    return dirMap;
  }


  //TODO move to propogator
  public void handleLinksAttributesChanges(
      TreeNode node, ChangesSet<Attribute> params, byte flag) {
    if (getLCM().getStorage(node.getTreeDB()) == null) return;
    Set<String> ofint = null;
    //Map<String, Attribute> oldA = params.getOldMap();
    for (String s:params.getAll()) {
      Attribute olda = params.getOld(s);
      Attribute a = params.getNew(s);
      if (a!=null && a.isReference() && a.isCP()
          || a == null && olda != null && olda.isReference() && olda.isCP()){
        if(ofint == null)
          ofint = new HashSet<>();
        ofint.add(s); 
      }
    }
    if(ofint!=null)
      //TODO cache lates?
      handleLinksAttributesChanges(node, ofint, params.getNewMap(), getLatesRelationsRecords(node.getTreeDB()), flag);
  }

  private static final void addValue(
      List<RelationRecord> list, List<RelationRecord> cplist, RelationRecord rr) {
    if(rr.isCP())
      cplist.add(rr);
  }
  private static final void addValue(
      List<RelationRecord> toa, List<RelationRecord> cptoa, TreeNode nodeFrom, TreeNode nodeTo, String key, boolean cp, String vers) {
    if(cp){
      cptoa.add(new RelationRecord(nodeFrom.getTreeDB().getLocalName(),nodeFrom.getTreeDB().getProjectName(), nodeFrom.getUUId(),
          nodeTo.getTreeDB().getLocalName(),nodeTo.getTreeDB().getProjectName(), nodeTo.getUUId(), key, vers));
    }
  }


  private ITreeChangeListener changeListener =
      new DefaultTreeChangeListener() {

        @Override
        public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {
          if(m == Moment.BEFORE)
            return;
          TreeDB dbfrom = node.getTreeDB();
          if (getLCM().getStorage(dbfrom) == null) return;
          recCheck(node);
        }

        @Override
        public void deleted(TreeNode nodeDesc, byte flag, Moment m) {
          TreeDB dbfrom = nodeDesc.getTreeDB();
          LinksCacheMan lcm = getLCM();
          if (lcm.getStorage(dbfrom) == null || m == Moment.AFTER) return;
          Set<TreeNode> nds = new HashSet<>();
          TreeDB activeDB = dbfrom;
          CacheStorage cs = getLCM().getStorage(activeDB);
          cs.writeLock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
          if(lcm.cachedRelations!=null)
            lcm.cachedRelations.remove(nodeDesc.getUUId());
          try{
            recAddTreeNodes(nodeDesc, nds);
            CacheTransactionDescription t = null;
            try{
              t = cs.startCacheTransaction("Delete node "+nodeDesc.getUUId(), CacheStorage.COMMIT_ALL);
              for (TreeNode node : nds) {
                removeIncorrectRevLinks(node);
                removeIncorrectLinks(node);
                //getStorage().removeCache(node.getSUUId());
                //TODO remove?
    //            getStorage().linkCache.remove(node);
    //            getStorage().revLinksCahce.remove(node);
              }
              if(t != null && t.error == null) {
                t.error("already commited"); 
                cs.commit(t);
              }
            }
            catch(Exception e){
              if(t != null && t.error == null)
                cs.rollback(t);
              throw e;
            }
          }finally{
            cs.writeUnlock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
          }

          //LinksStorage.removeRevKey(getStorage(),nodeDesc.getSUUId());
        }

        @Override
        public void created(TreeNode node, byte flag) {
          TreeDB dbfrom = node.getTreeDB();
          CacheTransactionDescription t = null;
          CacheStorage cs = getLCM().getStorage(dbfrom);
          Map<String, List<LateResolveRequest>> alates = getLatesRelationsRecords(dbfrom);
          cs.writeLock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
          try{
            t = cs.startCacheTransaction("resolve created links",CacheStorage.COMMIT_SINGLE);
            Set<String> ofint = null;
            for (String s : node.attrs().getLocalAttributeKeys()) {
              Attribute a = node.attrs().getAttribute(s);
              if (a == null || a.isReference() && a.isCP())
              {
                if(ofint == null)
                  ofint = new HashSet<>();
                ofint.add(s);
              }
            }
            if(ofint!=null && !ofint.isEmpty())
              handleLinksAttributesChanges(node, ofint, node.attrs().getLocalAttributes(), alates, flag);
            //if(getStorage().getStoredRevKeys().contains(node.getSUUId())){
            removeIncorrectLinks( node);
            removeIncorrectRevLinks(node);
            if(t != null && t.error == null) {
              t.error("already commited");
              cs.commit(t);
            }
            //}else
              //correctRevLinks(node);
          }
          catch(Exception e){
            if(t != null && t.error == null) 
              cs.rollback(t);
            throw e;
          }
          finally{
            cs.writeUnlock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
          }
        };

        

        private void removeIncorrectRevLinks(TreeNode node) {
          List<RelationRecord> rev = getLinksToCacheItems(node);
          //rl.unlock();
          List<RelationRecord> todel = null;
          if (rev != null && rev.size() > 0) {
            for (RelationRecord rr: rev){
              if(rr.isCP()){
                CheckResult cr = checkLinkIsCorrect(node.getTreeDB(), rr);
                if (cr != CheckResult.OK) {
                  if(todel == null)
                    todel = new LinkedList<>();
                  todel.add(rr);
                }
              }
            }
          }
          if(todel!=null){
            getLCM().updateCPLinks(node.getTreeDB(), todel, Collections.emptyList(), true);
            for (RelationRecord rr: todel){

              com.unitesk.requality.core.operation.IProgressMonitor mon = node.getTreeDB().getMonitor();//TODO add IDE monitor
              TreeDB dba = ResManager.getManager().getTreeDB(node.getTreeDB(),null, rr.projectFrom, true, mon);
              TreeNode nde = dba.getNode(rr.nodeFrom);
              if(nde!=null){
                nde.updateLocalAttribute(rr.key);
                nde.commitAttributes();
              }
            }
          }
        }

        private void recAddTreeNodes(TreeNode nd, Set<TreeNode> uids) {
          if (nd == null) return;
          TreeNode node = nd.getTreeDB().getNode(nd.getUUId());
          if (node != null) uids.add(node);
          for (TreeNode ndc : nd.getWorkChildren(null,true,false,true,false)) recAddTreeNodes(ndc, uids);
        }

        
        @Override
        public void changed(
            TreeNode node, ChangesSet<Attribute> params, Moment m, byte flag) {
          if(m == Moment.BEFORE)
            return;
          handleLinksAttributesChanges(node, params, flag);
          removeIncorrectLinks(node);
        }

        private void recCheck(TreeNode source) {
          removeIncorrectRevLinks(source);
          for (TreeNode node : source.getWorkChildren()) recCheck(node);
        }

        @Override
        public void changed(List<TreeNodeChangeEvent> changes) {
         // if (getLCM().getStorage(getTreeDB()) == null) return;
          for (TreeNodeChangeEvent ce : changes) {
            byte flag = ce.getFlag();
            switch (ce.getType()) {
              case CHANGE:
                {
                  if(ce.getMoment()==Moment.BEFORE)
                    continue;
                  TreeNode node = ce.getTreeNodeTarget(); 
                    changed(
                        node,
                        (ChangesSet<Attribute>) ce.getArguments()[0],
                        Moment.AFTER,
                        flag);
                }
                break;
              case MOVE:{
                if(ce.getMoment()==Moment.BEFORE)
                  continue;
                
                TreeNode node = ce.getTreeNodeTarget(); 
                  moved(
                      node,
                    (TreeNode) ce.getArguments()[0],
                    (TreeNode) ce.getArguments()[1],
                    Moment.AFTER, flag);
              }break;
              case CREATE:{
                if(ce.getMoment()==Moment.BEFORE)
                  continue;
                
                TreeNode node = ce.getTreeNodeTarget(); 
                  created(node, flag);
              }break;
              case DELETE:{
                if(ce.getMoment()==Moment.BEFORE)
                  continue;
                
                TreeNode node = ce.getTreeNodeTarget(); 
                  deleted(node, flag, ce.getMoment());
              }
              break;
              default:
                break;
            }
          }
        }

      };
  /***
   * Initiates revert links by direct links if this operation was not performed before.
   * The state is determined by flag in cache storage. If flag is not set then the whole
   * tree wil nodeLoaded and all reference
   * Also will cause name cache calculation
   * Also handles late reference calls for multi-project refs
   */
  private OperationResult initCaches(TreeDB db, IProgressMonitor mon) {
    return initCaches(db, false, mon);
  }
  private OperationResult initCaches(TreeDB dbb, boolean instance, IProgressMonitor mon) {
    TreeDB dba = dbb;
    stopped = false;
    String name = dba.getProjectName();
    dba.removeListener(changeListener);
    CacheStorage cps = getTracker().getCPCacheStorage(dba);
    Global.registerStoppable(this);
    if(mon == null)
      mon = ConsoleProgressMonitor.ONE;
    try{
      CacheTransactionDescription tr = cps.startCacheTransaction("init caches ", CacheStorage.COMMIT_ALL);
      try{
        initRevLinks(dba);
        if(!dba.getListeners().contains(changeListener))
          dba.addListener(changeListener);
          //loadLates();
        //for (TreeDB db : ResManager.getManager().getTreeDBs()) {
          //String fname = ResManager.getManager().getName(db);
       // fulfillLatesRequests(dba, name, mon);
      }
      finally{
        if(tr != null && tr.error == null && !mon.isCanceled())
          cps.commit(tr);
      }
    }finally{
      Global.unRegisterStoppable(this);
    }
    return mon.isCanceled()?OperationResult.UNSUCEEDED:OperationResult.SUCEEDED;
  }
  public void fulfillLatesRequests(TreeDB dba, String lname, String pname, IProgressMonitor mon){
    if(mon == null)
      mon = new ConsoleProgressMonitor();
    //TODO adds also by lname?
    IResTracker rt = irt;
    if(dba!=null)
      rt = dba.getTracker();
    CPLinkResolver lr = this;
    CacheStorage cs = lr.getLCM().getStorage(dba);
    CacheTransactionDescription ts = null;
    if(cs!=null)
      ts = cs.startCacheTransaction("Fullfill lates for "+lname, 2);
    try {
      TreeStateManager tsm = rt.getTreeStateManager();
      boolean changes = false;
      synchronized(getLatesLockObject(dba, pname)) {
        Map<String, List<LateResolveRequest>> lates = null;
        synchronized (LinksCacheMan.class) {
           if(dba!=null)
             lates = lr.getLatesRelationsRecords(dba);
           else
             lates = lr.getLatesRelationsRecords(rt.getCPCacheStorage(null));//TODO non null?
        }
        if (lates.containsKey(pname) || lates.containsKey(lname)) {
  //        rrwl.writeLock().lock();
          try{
            List<LateResolveRequest> lrr = lates.remove(pname);
            if(lrr == null)
              lrr = new LinkedList<>();
            List<LateResolveRequest> lrr2 = lates.remove(lname);
            if(lrr2!=null)
              lrr.addAll(lrr2);
            int c = lrr.size();
            Map<TreeNode,Set<String>> toch = new HashMap<>();
            Map<TreeNode, Map<String, LinkedList<LateResolveRequest>>> lrrs = new HashMap<>();
            for (LateResolveRequest l : new HashSet<>(lrr)) {
              if(mon.isCanceled())
                return;
              TreeDB db = rt.getTreeDB(dba,null, l.projName, false, mon);//TODO IDE monitor
              if(db != null && db.getCMM().getLinkManipulator().getNamesInited(db)){
                TreeNode node = db.getNode(l.uid);
                if (l!=null && node!=null && !node.isDisposed()){
                  if(!toch.containsKey(node)) {
                    toch.put(node, new HashSet<>());
                    lrrs.put(node, new HashMap<>());
                  }
                  toch.get(node).add(l.key);
                  if(!lrrs.get(node).containsKey(l.key))
                    lrrs.get(node).put(l.key, new LinkedList<>());
                  lrrs.get(node).get(l.key).add(l);
                  lrr.remove(l);
                  changes = true;
                }else
                  RLogger.logDebug("Found link to missing node from "+l.projName+" ("+l.uid+") with key "+l.key);
              }
            }
            if(changes) {
              for(TreeNode node:toch.keySet()){
                if(mon.isCanceled())
                  return;
                Set<String>links = toch.get(node);
                for(String s:links){
                  Attribute a = node.attrs().getAttribute(s);
                  if(a!=null && a.isReference() && a.isCP())
                    ReferenceValue.resetAttribute(node.attrs(),a.getType(),a.getValue());
                }
                if(!toch.get(node).isEmpty()) {
                  lrrs.remove(node);//remove old lates
                  node.getTreeDB().getTracker().getCPLinkResolver(node.getTreeDB()).handleLinksAttributesChanges(
                      node, toch.get(node), node.attrs().getLocalAttributes(), lates, (byte) (OFlag.NO_UPDATE));
                }
              }
//              if(!lrr.isEmpty())
//                lates.put(pname, lrr);
              synchronized (LinksCacheMan.class) {
                if(!mon.isCanceled())
                  lr.saveLatesRelationsToCache(dba, lates);
              }
            }//else
//              if(!lrr.isEmpty())
//                lates.put(pname, lrr);
          }finally{
  //          rrwl.writeLock().unlock();
          }
        }
      }
    }finally {
      if(cs!=null && ts!=null && ts.error == null)
        cs.commit(ts);
    }
  } 
  public void fulfillRevLatesRequests(TreeDB dba, String lname, String pname, IProgressMonitor mon){
    if(mon == null)
        mon = new ConsoleProgressMonitor();
      //TODO adds also by lname?
      IResTracker rt = irt;
      if(dba!=null)
        rt = dba.getTracker();
      CPLinkResolver lr = this;
      TreeStateManager tsm = rt.getTreeStateManager();
      boolean messaged = false;
      Map<String, List<LateResolveRequest>> directLatesMap = new HashMap<>();
      synchronized(getLatesLockObject(dba, pname)) {
        Map<String, List<LateResolveRequest>> revertlates = null; //pfrom->[pto, nodefrom, key]
        synchronized (LinksCacheMan.class) {
           if(dba!=null)
             revertlates = lr.getRevLatesRelationsRecords(dba);
           else
             revertlates = lr.getRevLatesRelationsRecords(rt.getCPCacheStorage(null));//TODO non null?
        }
        if (revertlates.containsKey(pname) || revertlates.containsKey(lname)) {
//            rrwl.writeLock().lock();
          try{
            boolean changed = false;
            List<LateResolveRequest> lrr = revertlates.get(pname);
            if(lrr == null)
              lrr = new LinkedList<>();
            List<LateResolveRequest> lrr2 = revertlates.get(lname);
            if(lrr2!=null)
              lrr.addAll(lrr2);
            int c = lrr.size();
            Map<TreeNode,Set<String>> toch = new HashMap<>();
            Map<TreeNode, Map<String, List<LateResolveRequest>>> lrrs = new HashMap<>();
            for (LateResolveRequest l : new HashSet<>(lrr)) {
              if(mon.isCanceled())
                return;
              TreeDB projTo = rt.getTreeDB(dba,null, l.projName, false, mon);//TODO IDE monitor
              if(projTo != null && projTo.getCMM().getLinkManipulator().getNamesInited(projTo)){
                if(dba == null) {
                  if(!messaged) {
                    RLogger.logDebug("Late resolve for non-treedb called for "+lname+" "+pname+". Cannot resolve it because it is not a tree.");
                    messaged = true;
                  }
                }
                TreeNode node = dba!=null?dba.getNode(l.uid):null;
                if (l!=null && node!=null && !node.isDisposed()){
                  if(!toch.containsKey(node)) {
                    toch.put(node, new HashSet<>());
                    lrrs.put(node, new HashMap<>());
                  }
                  toch.get(node).add(l.key);
                  if(!lrrs.get(node).containsKey(l.key))
                    lrrs.get(node).put(l.key, new LinkedList<>());
                  lrrs.get(node).get(l.key).add(l);
                  lrr.remove(l);
                  changed = true;
                }
              }
            }
            if(changed) {
              for(Entry<TreeNode, Set<String>> en:toch.entrySet()){
                if(mon.isCanceled())
                  return;
                TreeNode node = en.getKey();//node from p1
                Set<String>links = en.getValue();
                for(String s:links){
                  Attribute a = node.attrs().getAttribute(s); //key from p1
                  if(a!=null && a.isReference() && a.isCP())
                    ReferenceValue.resetAttribute(node.attrs(),a.getType(),a.getValue());
                  //try to reset ref
                }
                if(dba == null) {
                  if(!messaged) {
                    RLogger.logDebug("Late resolve for non-treedb called for "+lname+" "+pname+". Cannot resolve it because it is not a tree.");
                    messaged = true;
                  }
                  dba = node.getTreeDB();//TODO mb bad
                }
                for(Entry<String, List<LateResolveRequest>> nodeLinks:lrrs.get(node).entrySet()) {
                  List<LateResolveRequest> list = nodeLinks.getValue();
                  if(list != null) {
                    lrr.removeAll(list);
                    revertlates.get(nodeLinks.getKey()).removeAll(list);
                  }else
                    RLogger.logError("Problem during late links hanling - list for "+nodeLinks.getKey()+" is null");
                }
                directLatesMap.clear();
                dba.getTracker().getCPLinkResolver(dba).handleLinksAttributesChanges(
                    node, en.getValue(), node.attrs().getLocalAttributes(), directLatesMap, (byte) (OFlag.NO_UPDATE|OFlag.NO_LATES));
                for(Entry<String, List<LateResolveRequest>> dlen:directLatesMap.entrySet()) {
                  List<LateResolveRequest> requests = dlen.getValue();
                  for(LateResolveRequest request : requests) {
                    LateResolveRequest late = new LateResolveRequest(request.uid, null, dlen.getKey(), request.key);
                    if(dlen.getKey().equals(pname))
                      lrr.add(late);//rev version
                    else
                      revertlates.get(dlen.getKey()).add(late);
                  }
                }
              }
              revertlates.remove(lname);
              if(!lrr.isEmpty()) {
                revertlates.put(pname, lrr);
              }else
                revertlates.remove(pname);
            }
          }finally{
//              rrwl.writeLock().unlock();
            synchronized (LinksCacheMan.class) {
              if(!mon.isCanceled())
                lr.saveRevLatesRelationsToCache(lname, pname, dba, revertlates);
            }
          }
        }
      }
    }
  public void removeCPCacheForNode(TreeDB db, String projLocalName, String projProjectName, UUID node) {
    getLCM().removeCPRecords(db, projLocalName,projProjectName, node);
    //getNCM().deleteName(node);
  }
//
//  public List<TreeNode> resolveLink(
//      LinkResolver lr,
//      TreeNode where,
//      String key,
//      TreeNode def,
//      final String savedName,
//      boolean lock,
//      boolean save) {
//    return getStorage().resolveLink(lr, where, key, def, savedName, lock, save);
//  }

  public Map<String, Map<TreeNode, String>> getLinkedNodes(TreeNode node) {
    return getLinksFromNode(node);
  }

  public Map<String, List<LateResolveRequest>> loadLatesRelationsRecordsFromList(List<RelationRecord> ret) {
    Map<String, List<LateResolveRequest>> lates = null;
    for(RelationRecord reta:ret){
      String pto = reta.projectTo;
      if(lates == null)
        lates = new HashMap<>();
      if(!lates.containsKey(pto))
        lates.put(pto, new LinkedList<>());
      lates.get(pto).add(new LateResolveRequest(reta.nodeFrom,reta.localProjectFrom, reta.projectFrom, reta.key));
    }
    return lates==null?Collections.emptyMap():lates;
  }


  private List<RelationRecord> getLinksToCacheItems(
      TreeNode node) {
   
    TreeDB dba = node.getTreeDB();
    CacheStorage cs = dba.getTracker().getCPCacheStorage(dba);
    cs.readLock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
    try{
        List<RelationRecord> cp = dba.getTracker().getCPLinkResolver(dba).getLCM().getCPRevLinks(
            node);
        return cp;
    }
    finally{
      cs.readUnlock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
    }
  }
  private List<RelationRecord> getDirectLinksCacheItems(
      TreeNode node, Set<String> ofint) {
    TreeDB dba = node.getTreeDB();
    CacheStorage cs = dba.getTracker().getCPCacheStorage(dba);
    cs.readLock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
    try{
      List<RelationRecord> cp = dba.getTracker().getCPLinkResolver(dba).getLCM().getCPDirectLinks(node, ofint);
      return cp;
    }
    finally{
      cs.readUnlock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
    }

  }
  private List<RelationRecord> getDirectLinksCacheItems(
      TreeNode node) {
    TreeDB dba = node.getTreeDB();
    CacheStorage cs = dba.getTracker().getCPCacheStorage(dba);
    cs.readLock(LinksCacheMan.REVERT_LINKS_SECTION_NAME);
    try{
      return dba.getTracker().getCPLinkResolver(dba).getLCM().getCPDirectLinks(node);
    }
    finally{
      cs.readUnlock(LinksCacheMan.REVERT_LINKS_SECTION_NAME);
    }
    
  }
  private String getCPId(TreeNode where){
    return where.getTreeDB().getProjectName()+"::"+where.getSUUId();
  }
  private static boolean isCPRelation(TreeNode where, TreeNode nodeToSave){
    return !Instantiator.getRootTree(where.getTreeDB()).equals(Instantiator.getRootTree(nodeToSave.getTreeDB()));
  }

//  private void removeRevLink(TreeNode from, TreeNode to, String key, boolean lock) {
//    if(from==null || to == null)
//      return;
//    String k = to.getSUUId();
//    getCacheStorage().removeCacheListValue(k, "revert_revItem", key,from.getSUUId());
//    from.attrs().updateAttribute(key);
//    from.commitAttributes();
//  }

//  private void setRevInited(String root, String val){
//    TreeDB db = getTreeDB();
//    String s = (String) db.getPSCacheMan().getProjectSetting(PROJECT_SETTS_REV_CACHE_INITED);
//    if(s==null || !val.equals(s))
//      db.getPSCacheMan().setProjectSetting(PROJECT_SETTS_REV_CACHE_INITED, val);
//  }
  private void initRevLinks(TreeDB dba) {
    TreeDB db = dba;
    TreeNode root = null;
    if(db!=null)
      root = db.getRootNode();
    if (root == null) return;
    UUID rootuuid = root.getUUId();
    if(db == null){
      return;
    }
  //  boolean val = db.getTracker().getCMM(db)
  //      .getSettingsStore().getBooleanSetting(getTreeDBId(REV_INITED, db),false);
//    if (!val) {
      DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(root, false, false, true, false, false);
      Iterator<TreeNode> it = dftw.iterator();
//      saveRevLinks(null, true);
      long l = System.currentTimeMillis();
      CacheTransactionDescription vala = null;
      CacheStorage cs = root.getTreeDB().getTracker().getCPCacheStorage(root.getTreeDB());
      try{
        if(stopped)
          return;
        vala = cs.startCacheTransaction("init links",CacheStorage.COMMIT_ALL);
        Set<String> ofint = new HashSet<>();
        Map<String, List<LateResolveRequest>> alates = getLatesRelationsRecords(db);
        while (it.hasNext()) {
          if(stopped)
            return;
          TreeNode node = it.next();
          for (String key : new HashSet<>(node.attrs().getLocalAttributeKeys())) {
            Attribute a = node.attrs().getAttribute(key);
            if (a != null)
              if (a.isReference()&&a.isCP())
                ofint.add(key);
          }
          if(!ofint.isEmpty())
            handleLinksAttributesChanges(
                node, ofint, node.attrs().getLocalAttributes(), alates, OFlag.NO_UPDATE);//not save lates for a moment
          ofint.clear();
        }
        saveLatesRelationsToCache(cs, alates);
        //tends that it was already saved
        //dba.getTracker().getCPLinkResolver(dba).saveLatesRelationsToCache(dba, getLatesRelationsRecords(dba));
        //setRevInited(rootuuid.toString(), "true");

      }
      catch(Exception e){
        if(vala != null && vala.error == null) {
          vala.error("Already reversed");
          cs.rollback(vala);
        }
        e.printStackTrace();
        throw e;
      }
      finally {
        if(vala != null && vala.error == null){
          vala.error("Already commited");
          cs.commit(vala);
        }
      }
      if(CoreUtils.DEBUG_LEVEL>0) {
        RevisionInfo ri = TreeStateManager.getOne(dba.getTracker()).getRevisionInfo(db);
        RLogger.logDebug("rslv " + (System.currentTimeMillis()-l) +" "+root.getTreeDB().getVisibleName()+ ((ri==null)?" working":(" "+ri)));
      }
//      saveRevLinks(null, true);
   // }
  }
  public Map<UUID,String> getLinksToNode(TreeDB dbs, UUID uid, String key, boolean cp) {
    List<RelationRecord> st = null;
    TreeDB dba = dbs;
    if(cp)
    {
      CacheStorage cs = dba.getTracker().getCPCacheStorage(dba);
      cs.readLock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
      try{
        st = dba.getTracker().getCPLinkResolver(dba).getLCM().getCPRevLinks(dbs, uid, key); 
      }
      finally{
        cs.readUnlock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
      }
    }else
      st = getLCM().getRevLinks(dbs, uid);
    Map<UUID, String> revs = null;
    //TODO optimize for cp
    if(st!=null)
    for (RelationRecord rr : st) {
      TreeDB db = dba,adb = db;
      if(rr.projectFrom!=null&&rr.isCP()){
        adb = db.getTracker().getTreeDB(db,null, rr.projectFrom, false, db.getMonitor());//TODO IDE monitor
        //key = key;//it is not use :(
      }
      ITreeStorage sta = adb.getStorage();
      UUID nodeFrom = rr.nodeFrom;
      if(nodeFrom != null && sta.canLoad(nodeFrom)){
        if(revs == null)
          revs = new HashMap<>();
        revs.put(nodeFrom, rr.nodeToVersion);
      }
    }
    if(revs == null)
      return Collections.emptyMap();
    return revs;
  }
  public Map<String, Map<TreeNode,String>> getLinksToNode(TreeNode node, boolean cp, boolean validate) {
    List<RelationRecord> st = null;
    TreeDB dba = node.getTreeDB();
    if(cp)
    {
      CacheStorage cs = dba.getTracker().getCPCacheStorage(dba);
      cs.readLock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
      try{
        st = dba.getTracker().getCPLinkResolver(dba).getLCM().getCPRevLinks(node); 
      }
      finally{
        cs.readUnlock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
      }
    }else
      st = getLCM().getRevLinks(node);
    Map<String,Map<TreeNode, String>> revs = null;
    //TODO optimize for cp
    if(st!=null)
    for (RelationRecord rr : st) {
      String key = rr.key;
      TreeDB db = dba,adb = db;
      if(rr.projectFrom!=null&&rr.isCP()){
        adb = db.getTracker().getTreeDB(db,null, rr.projectFrom, false, db.getMonitor());//TODO IDE monitor
        //key = key;//it is not use :(
      }
      TreeNode nodeFrom = null;
      try{
        if(adb!=null)
          nodeFrom = adb.getNodeExc(rr.nodeFrom);
      }catch(NodeNotFoundException nfe){
        RLogger.logDebug(nfe.toString() + CoreUtils.getTrace(nfe), 3);
      }
      if(nodeFrom != null && !nodeFrom.isDisposed()){
        Attribute a = nodeFrom.attrs().getAttribute(key);
        if (a != null && nodeFrom.attrs().isReference(key)) {
          boolean isvalid = !validate;
          String akey = key;
          boolean mult = false;
          int al = 0;
          ;
          if(validate){
            if (a.getType() == AttributeType.REFERENCE) {
              isvalid =
                  nodeFrom
                      .getTreeDB()
                      .getLinkResolver()
                      .testNodeByPath(nodeFrom, node, ((ReferenceValue)a.getValue()).getRaw().toString());
            } else {
              List<Object> list = ((ListValue) a.getValue()).getValues();
              int l = 0;
              mult = list.size() > 1;
              for (Object o2 : list) {
                if (nodeFrom
                    .getTreeDB()
                    .getLinkResolver()
                    .testNodeByPath(nodeFrom, node, ((ReferenceValue)o2).getRaw().toString())) {
                  isvalid = true;
                  al = l;
                }
                l++;
              }
            }
          }
          if (isvalid) {
            if(revs == null)
              revs = new HashMap<>();
            if(!revs.containsKey(key))
              revs.put(key, new HashMap<>());
            revs.get(key).put(nodeFrom, rr.nodeToVersion);
//              getStorage().addLink(node2, node, key, true);
//              getStorage().saveRevLinks(node);
          }
        }
      }
    }
    if(revs == null)
      return Collections.emptyMap();
    return revs;
  }
  public Map<UUID, Map<String, Map<TreeNode, String>>> getAllLinksToNode(TreeDB dba) {
    List<RelationRecord> st = null;
      CacheStorage cs = dba.getTracker().getCPCacheStorage(dba);
      cs.readLock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
      try{
        st = dba.getTracker().getCPLinkResolver(dba).getLCM().getAllCPRevLinks(dba); 
      }
      finally{
        cs.readUnlock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
      }
      Map<UUID, Map<String, Map<TreeNode, String>>> revs = null;
    //TODO optimize for cp
    if(st!=null)
    for (RelationRecord rr : st) {
      String key = rr.key;
      TreeDB db = dba,adb = db;
      if(rr.projectFrom!=null&&rr.isCP()){
        adb = db.getTracker().getTreeDB(db,null, rr.projectFrom, false, db.getMonitor());//TODO IDE monitor
        //key = key;//it is not use :(
      }
      TreeNode nodeFrom = null;
      try{
        if(adb!=null)
          nodeFrom = adb.getNodeExc(rr.nodeFrom);
      }catch(NodeNotFoundException nfe){
        RLogger.logDebug(nfe.toString() + CoreUtils.getTrace(nfe), 3);
      }
      if(nodeFrom != null && !nodeFrom.isDisposed()){
        Attribute a = nodeFrom.attrs().getAttribute(key);
        if (a != null && nodeFrom.attrs().isReference(key)) {
          boolean isvalid = true;
          String akey = key;
          boolean mult = false;
          int al = 0;
          if(revs == null)
            revs = new HashMap<>();

          if(!revs.containsKey(rr.nodeTo))
            revs.put(rr.nodeTo, new HashMap<>());
          if (isvalid) {
            if(!revs.get(rr.nodeTo).containsKey(key))
              revs.get(rr.nodeTo).put(key, new HashMap<>());
            revs.get(rr.nodeTo).get(key).put(nodeFrom, rr.nodeToVersion);
          }
        }
      }
    }
    if(revs == null)
      return Collections.emptyMap();
    return revs;
  }
  public Map<UUID, Map<String, Map<TreeNode, String>>> getAllLinkedNodes(TreeDB dba) {
    List<RelationRecord> st = null;
    CacheStorage cs = dba.getTracker().getCPCacheStorage(dba);
    cs.readLock(LinksCacheMan.REVERT_LINKS_SECTION_NAME);
    try{
      st = dba.getTracker().getCPLinkResolver(dba).getLCM().getAllCPDirectLinks(dba);
    }
    finally{
      cs.readUnlock(LinksCacheMan.REVERT_LINKS_SECTION_NAME);
    }
    Map<UUID,Map<String, Map<TreeNode, String>>> revs = null;
    if(st!=null)
    for (RelationRecord rr: st) {
      String key = rr.key;
      //String val = stored.get(key);
      //if (val == null) continue;
      //String[] paths = val.split("\\|");
      TreeDB db = dba, adb = db;
      if(rr.isCP()){
        adb = db.getTracker().getTreeDB(db,null, rr.projectTo, false, db.getMonitor());//TODO IDE monitor
        key = rr.projectTo+TermsManipulator.SEPARATOR+key;
      }
      TreeNode nodeTo = null;
      if(adb!=null)
        nodeTo = adb.getNode(rr.nodeTo);
      if(revs == null)
        revs = new TreeMap<>();
      if(!revs.containsKey(rr.nodeFrom))
        revs.put(rr.nodeFrom, new HashMap<>());
      if(nodeTo!=null && !nodeTo.isDisposed()){
        if(!revs.get(rr.nodeFrom).containsKey(key))
          revs.get(rr.nodeFrom).put(key, new TreeMap<>());
        revs.get(rr.nodeFrom).get(key).put(nodeTo, rr.nodeToVersion);
      }
    }
    if(revs == null)
      return Collections.emptyMap();
    return revs;
  }
  private Map<String, Map<TreeNode, String>> getLinksFromNode(TreeNode node) {
    List<RelationRecord> st = null;
    TreeDB dba = node.getTreeDB();
    CacheStorage cs = dba.getTracker().getCPCacheStorage(dba);
    cs.readLock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
    try{
      st = dba.getTracker().getCPLinkResolver(dba).getLCM().getCPDirectLinks(node);
    }
    finally{
      cs.readUnlock(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME);
    }
    
    Map<String, Map<TreeNode, String>> revs = null;
    if(st!=null)
    for (RelationRecord rr: st) {
      String key = rr.key;
      //String val = stored.get(key);
      //if (val == null) continue;
      //String[] paths = val.split("\\|");
      TreeDB db = dba, adb = db;
      if(rr.isCP()){
        adb = db.getTracker().getTreeDB(db,null, rr.projectTo, false, db.getMonitor());//TODO IDE monitor
        key = rr.projectTo+TermsManipulator.SEPARATOR+key;
      }
      TreeNode nodeTo = null;
      if(adb!=null)
        nodeTo = adb.getNode(rr.nodeTo);
      if(nodeTo!=null && !nodeTo.isDisposed()){
        if(revs == null)
          revs = new TreeMap<>();
        if(!revs.containsKey(key))
          revs.put(key, new TreeMap<>());
        revs.get(key).put(nodeTo, rr.nodeToVersion);
      }
    }
    if(revs == null)
      return Collections.emptyMap();
    return revs;
  }

  @Override
  protected OperationResult innerInitFirst(TreeDB db, SettingsManipulator cman, IProgressMonitor mon) {
    try {
      return initCaches(db, mon);
    }finally {
      stopped = false;
      String name = db.getProjectName();
      //fulfillLatesRequests(db, name, mon);
    }
  }
  @Override
  protected OperationResult innerDropCache(TreeDB db, SettingsManipulator cman) {
    String lname = db.getLocalName();
    String pname = db.getProjectName();
    getLCM().removeAllCPRevCacheByProject(db,lname, pname);
    LinksCacheMan.removeLates(db, pname);
    return OperationResult.SUCEEDED;
  }

  @Override
  public boolean isCrossProjectCache() {
    return true;
  }

  @Override
  public OperationResult innerInitNotFirst(TreeDB dba, SettingsManipulator cman, IProgressMonitor mon) {
    CacheStorage cps = dba.getTracker().getCPCacheStorage(dba);
    
    dba.removeListener(changeListener);
    CacheTransactionDescription tr = cps.startCacheTransaction("init caches ", CacheStorage.COMMIT_ALL);
    try{
      fulfillRevLatesRequests(dba, dba.getLocalName(), dba.getProjectName(), mon);
    }
    finally{
      if(tr != null && tr.error == null)
        cps.commit(tr);
      if(mon.isCanceled())
        return OperationResult.UNSUCEEDED;
    }
    if(!dba.getListeners().contains(changeListener))
      dba.addListener(changeListener);
    return OperationResult.SUCEEDED;
  }

  @Override
  protected String[] getSettingsNames(TreeDB db) {
    return new String[]{getCPTreeDBId(LinksCacheMan.LATE_SECTION_NAME, db),
      getCPTreeDBId(LinksCacheMan.GLOBAL_REVERT_LINKS_SECTION_NAME, db)};
  }
  private static String[] CP_DEFS = new String[]{"1.2.2","1.2.2"};
  @Override
  protected String[] getVersions() {
    return CP_DEFS;
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

  public static synchronized Object getLatesLockObject(TreeDB db, String name2) {
    if(!lateLoadingLocks.containsKey(name2))
      lateLoadingLocks.put(name2, name2+"_lateConcurrentLock");
    return lateLoadingLocks.get(name2);
  }

}
