package com.unitesk.requality.core.instancer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.DefaultUUIDGenerator;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.create.CreateNode;
import com.unitesk.requality.core.storage.IStorageSource;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.InstancesFolder;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.RestrictedFolder;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.CoreUtils;

public class Instantiator {
  private static final String PARENT_ROOT = "_%parentRoot";
  private static final String INST_NAME = "_%instName";
  public static final String ATTR_TEMP = "_temporary";
  public static String DEFAULT_INSTANCE_NAME = "_default";
  private static Map<String, TreeDB> rootToDBMap = new HashMap<>(); // TODO
  // move
  // to
  // treedb
  // load
  // mech?
  private static Map<String, TreeInstancer> rootToTI = new HashMap<>(); // TODO
  // move
  // to
  // treedb
  // load
  // mech?
  private static Map<TreeDB, TreeDB> iDBToRootDB = new HashMap<>();
  private static Set<String> initiated = new HashSet<>();
  // private static Map<UUID,TreeDB> iRootToInstanceTreeDBs = new
  // HashMap<UUID, TreeDB>();
  private static ReentrantReadWriteLock instLock = new ReentrantReadWriteLock();
  private static ReadLock rl = instLock.readLock();
  private static WriteLock wl = instLock.writeLock();
  private static Map<String, Map<String, String>> rootToInstances =
      new HashMap<>();

  private static void recClear(String rid) {
    if (rootToInstances.containsKey(rid)) {
      for (String name : rootToInstances.get(rid).keySet())
        recClear(rootToInstances.get(rid).get(name));
      rootToInstances.remove(rid);
    }
    initiated.remove(rid);
    
    rootToTI.remove(rid);
    //initiated.remove(rid);
    TreeDB db = rootToDBMap.remove(rid);
    if (db != null) iDBToRootDB.remove(db);

    for(String s:new HashSet<>(rootToTI.keySet()))
      if(s.startsWith(rid.substring(0,rid.indexOf("|"))))
        recClear(s);//TODO rewrite
  }

  public static void clearCache(TreeDB db) {
    try {
      UUID node = db.getRootNodeUUID();
      if(node == null)
        return;
      String root = node.toString();
      String rid = getId(root, db);
      recClear(rid);
    } catch (Exception e) {
      RLogger.logError(e);
    }
  }

  public static String getId(String uid, TreeDB tree) {
    return getId(uid, tree, false);
  }

  public static String getId(String uid, TreeDB tree, boolean isroot) {
    if (uid == null) return null;
    if (!isroot && tree.isVirtual()) tree = getRootTree(tree);
    return tree.getLocalName() + "|" + uid;
  }

  public static TreeDB getRootTree(TreeDB db) {
    return getRootTree(db, TreeDB.DEFAULT_DEPTH);
  }

  public static TreeDB getRootTree(TreeDB db, int deep) {
    if (db == null  || deep < 1) return db;
    // String dbid = getId(db.getRootNodeUUID(),db);
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    TreeDB moreRoot = tsm==null?null:tsm.getBaseTree(db);
    if (moreRoot==null) {
      String qid = getId(db.getRootNodeSUUID(), db, true);
      rootToDBMap.put(qid, db);
      return db;
    }
    if(moreRoot!=db)
      return getRootTree(moreRoot, deep-1);
    return db;
  // return
    // rootToDBMap.get(getRoo(getId(db.getRootNodeUUID(),db),deep-1));
  }
//
//  private static boolean initIfNeeded(TreeDB db) {
//    return initIfNeeded(db, false);
//  }
//
//  private static boolean initIfNeeded(TreeDB db, boolean isroot) {
//    db = getRootTree(db, TreeDB.DEFAULT_DEPTH);
//    if (!db.isVirtual()
//        && db.isLoaded()
//        && !initiated.contains(getId(db.getRootNodeUUID(), db, isroot))) {
//      writeLock();
//      try {
//        // TreeNode node = db.getNode("/Instances", Path.QID);
//        UUID root = db.getRootNodeUUID();
//        initiated.add(getId(db.getRootNodeUUID(), db, isroot));
//        // rootToInstances.put(root,new HashMap<String, UUID>());
//        UUID node = null;
//        for (UUID uid : db.getStorage().getChildren(root))
//          if (db.getStorage().getNodeId(uid, true).equals("Instances")) {
//            node = uid;
//            break;
//          }
//        if (node != null) {
//          for (UUID chi : db.getStorage().getChildren(node)) {
//            if (chi != null && db.isNodeExist(chi)) {
//              Map<String, AttributeDefinition> attrs = db.getStorage().getAttributes(chi);
//              AttributeDefinition temp = attrs.get(INST_NAME);
//              String name = null;
//              if (temp != null && temp.getRawValue() != null) {
//                Object o = temp.getRawValue().getValue();
//                if (o != null) name = o.toString();
//              }
//              temp = attrs.get(PARENT_ROOT);
//              String droot = null;
//              if (temp != null && temp.getRawValue() != null) {
//                Object o = temp.getRawValue().getValue();
//                if (o != null) droot = o.toString();
//              }
//              if (droot != null) {
//                UUID dr = getUUID(droot);
//                String idr = getId(dr, db, isroot), ichi = getId(chi, db, isroot);
//                if (!rootToInstances.containsKey(idr))
//                  rootToInstances.put(idr, new HashMap<String, String>());
//                rootToInstances.get(idr).put(name, ichi);
//              }
//            }
//          }
//        }
//        return true;
//      } catch (Exception e) {
//        RLogger.logError(e);
//      }
//      finally{
//        writeUnlock(); 
//      }
//    }
//    return false;
//  }

  // 1 step upper
  public static TreeDB getBaseTree(TreeDB fakedb) {
    readLock();
    try{
      if (!fakedb.isVirtual()) return fakedb;
      // initIfNeeded(fakedb);
      // UUID uid = fakedb.getRootNodeUUID();
      // String iuid = getId(uid,fakedb);
      TreeDB root = iDBToRootDB.get(fakedb);
      if (root != null) return root;
      return null;
    }
    finally{
      readUnlock();
    }
      // return revInstanceTreeDBs.get(fakedb);
  }

  // private static TreesTracker getTracker(TreeDB db){
  // if(db!=null && !(db.getStorage() instanceof FSStorage)){
  // ITreeStorage ts = VirtualNode.getRootTree(db).getStorage();
  // while(ts instanceof TransactionStorage)
  // ts = ((TransactionStorage)ts).getStorage();
  // if(!(ts instanceof FSStorage))
  // return TreesTracker.getTracker();
  // }
  // return null;
  //
  // }
//  public static TreeDB getVirtualTreeDB(TreeDB db, String name) {
//    return getVirtualTreeDB(db, name, null, null, false);
//  }
//
//  public static TreeDB getVirtualTreeDB(TreeDB db, String name, boolean force) {
//    return getVirtualTreeDB(db, name, null, null, force);
//  }

  private static List<TreeDB> instLoading = new ArrayList<>();

  public static boolean haveVirtualTreeDB(TreeDB db, String uuId) {
    readLock();
    try{
      if(db.isVirtual())
        db = getRootTree(db);
      //UUID uroot = db.getRootNodeUUID();
      //if (uroot == null) return false;
      if (uuId == null) return true; // def//getExistVirtualTreeDB(db, DEFAULT_INSTANCE_NAME,
      // null, null)!=null;
      //initIfNeeded(db);
      // Map<String,String> map=rootToInstances.get(getId(uroot,db));
      String id = getId(uuId, db);//, whid = getId(uuId, db);
      TreeDB name = getExistVirtualTreeDBByRoot(db, id);
      if (name != null) {
        return true;
      }
      // TODO check
      return false;
    }
    finally{
      readUnlock();
    }
  }

  private static String getVTreeName(String where, String what) {
    readLock();
    try{
      Map<String, String> map = rootToInstances.get(where);
      if (map != null) {
        for (String s : map.keySet()) if (map.get(s).equals(what)) return s;
        for (String s : map.keySet()) {
          String r = getVTreeName(map.get(s), what);
          if (r != null) return r;
        }
      }
    }
    finally{
      readUnlock();
    }
    return null;
  }
//
//  private static TreeDB getVTreeDB(TreeDB db, UUID what) {
//    if (db == null || db.getStorage() == null) return null;
//    initIfNeeded(db);
//    readLock();
//    try{
//      UUID where = db.getRootNodeUUID();
//      String wid = getId(where, db), whid = getId(what, db);
//      Map<String, String> map = rootToInstances.get(wid);
//      if (map != null) {
//        for (String s : map.keySet())
//          if (map.get(s).equals(whid)){
//            readUnlock();
//            try{
//              return getVirtualTreeDB(db, s, null, null, true);
//            }
//            finally{
//              readLock();
//            }
//          }
//        for (String s : map.keySet()) {
//          readUnlock();
//          try{
//            if (getVirtualTreeDB(db, s, null, null, true) == db) continue;
//            TreeDB r = getVTreeDB(getVirtualTreeDB(db, s, null, null, true), what);
//            if (r != null) return r;
//          }finally{
//            readLock();
//          }
//        }
//      }
//      return null;
//    }
//    finally{
//      readUnlock();
//    }
//  }

  public static TreeDB getExistVirtualTreeDB(TreeDB db, String uuId) {
    // if(!db.isLoaded())
    // return null;
    // TODO look
    TreeDB root = db;
    if(db.isVirtual())
      root = getRootTree(db);
    String uroot = db.getRootNodeSUUID();
    if (uroot == null) return root;
    //removed  || !root.isNodeExist(UUID.fromString(uuId))
    if (uuId == null) return db;
    // return getVirtualTreeDB(db, DEFAULT_INSTANCE_NAME, null, null);
    // if(canReach(uroot,uuId))
    //initIfNeeded(db);
    String whid = getId(uuId, db);
    TreeDB rch = getExistVirtualTreeDBByRoot(db, whid);
    if (rch != null) return rch;
    return root;//TODO checl
  }
//
//  public static TreeDB getVirtualTreeDB(
//      TreeDB db, String name, final TreeInstancer prevInst, Map<String, Object> initPar) {
//    return getVirtualTreeDB(db, name, prevInst, initPar, false);
//  }

//  public static TreeDB getVirtualTreeDB(
//      TreeDB db,
//      String name,
//      final TreeInstancer prevInst,
//      Map<String, Object> initPar,
//      boolean force) {
//    // TODO avoid transaction?
//  }

  private static ITreeChangeListener delListener(TreeDB db, final TreeDB instTree) {
    return new DefaultTreeChangeListener() {

      @Override
      public void deleted(TreeNode node, byte flag, Moment m) {
        // delete(node.getUUId(),node.getWorkParentUUID());
        if(m == Moment.BEFORE)
          return;
        if (isVRoot(node)) {
          TreeDB root = Instantiator.getRootTree(node.getTreeDB());
//          boolean ex = haveVirtualTreeDB(instTree, node.getUUId());
//          if (ex)
            removeInstance(instTree, node.getSUUId());
            TreeNode ir = root.getNode("/Instances");
            if(ir!=null&&ir.getWorkChildrenUUIDs(true).size()==0)
              ir.delete();
        }
      }
    };
  }

  private static TreeInstancer loadOrCreateTi(
      String instR, final TreeDB db, final TreeInstancer prevInst, Map<String, Object> initPar) {
    String insti = getId(instR, db);
    if (rootToTI.containsKey(insti)) return rootToTI.get(insti);
    TreeInstancer ti = null;
    // root storage to create raw root
    TreeDB rootT = db;
    if(rootT.isVirtual())
      rootT = getRootTree(db);
    String rt = rootT.getStorage().getNodeId(UUID.fromString(instR)); // root.getNode(instR);
    if (prevInst != null) {
      // is it proper??
      IStorageSource ms =
          new IStorageSource() {

            @Override
            public ITreeStorage getBaseStorage() {
              return prevInst;
              // MemoryStorage ms = new MemoryStorage(finstR);
              // ms.setDeltaTarget(fdb.getNullDeltaListener());
              // return ms;
            }
          };
      // TODO rename?
      ti = new TreeInstancer(db, instR, rt, ms, prevInst);
    } else {
      // may be wrong

      IStorageSource ms =
          new IStorageSource() {
            @Override
            public ITreeStorage getBaseStorage() {
              return db.getStorage();
            }
          };
      ti = new TreeInstancer(db, instR, rt, ms);
    }
    rootToTI.put(insti, ti);
    return ti;
  }
//
//  public static TreeDB getExistVirtualTreeDB(
//      TreeDB db, String name, TreeInstancer prevInst, Map<String, Object> initPar) {
//    // TODO avoid transaction?
//    // if(!db.isLoaded())
//    // return null;
//    if (db.isVirtual() && db.getLevelOfAbstraction() >= 3) return db;
//    UUID uroot = db.getRootNodeUUID();
//    if (uroot == null) return null;
//    initIfNeeded(db);
//    String iuroot = getId(uroot, db);
//    Map<String, String> map = rootToInstances.get(iuroot);
//
//    if (map == null) map = new HashMap<>();
//    // String nme = db.getName();
//    // if(nme == null)
//    String nme = "";
//    String instR = map.get(nme + name);
//    if (instR == null && !instLoading.contains(db)) {
//      return null;
//    }
//    TreeDB instTree = rootToDBMap.get(instR);
//    if (instTree == null && instR != null) instTree = loadDB(db, prevInst, getUUID(instR), initPar);
//    return instTree;
//  }

  private static String getUUID(String instR) {
    int pos = instR.lastIndexOf("|");
    if (pos > -1) return instR.substring(pos + 1);
    return null;
  }

  public static void loadDBs(
      TreeDB db, IProgressMonitor pm) {
    // root storage to create raw root
    if(db == null)
      return;
    TreeDB root = db;
    if(db.isVirtual())
      root = getRootTree(db);
    String id = getId(db.getRootNodeSUUID(), db);
    if(initiated.contains(id))//TODO clear it
      return;
    writeLock();
    try{
      //TreeNode rt = root.getNode("/Instances");
      String rid = root.getRootNodeSUUID();
      initiated.add(getId(rid, db));
      UUID node = null;
      for (UUID uid : db.getStorage().getChildren(UUID.fromString(rid)))
        if (db.getStorage().getNodeId(uid).equals("Instances")) {
         node = uid;
         break;
        }
      String instR = null;
      if (node != null) {
        for (UUID chi : db.getStorage().getChildren(node)) {
          if(pm.isCanceled()) return;
          if (chi != null && db.isNodeExist(chi)) {
            String nid = db.getStorage().getNodeId(chi);
            Map<String, AttributeDefinition> attrs = db.getStorage().getAttributes(chi);
            AttributeDefinition temp = attrs.get(INST_NAME);
            String name = null;
            if (temp != null && temp.getRawValue() != null) {
              Object o = temp.getRawValue().getValue();
              if (o != null) name = o.toString();
            }
            temp = attrs.get(PARENT_ROOT);
            String droot = null;
            if (temp != null && temp.getRawValue() != null) {
              Object o = temp.getRawValue().getValue();
              if (o != null) droot = o.toString();
            }
            if (droot != null) {
              String dr = getUUID(droot);
              String idr = getId(dr, db), ichi = getId(chi.toString(), db);
              if(pm.isCanceled()) return;
              if (!rootToInstances.containsKey(idr))
                rootToInstances.put(idr, new HashMap<String, String>());
              rootToInstances.get(idr).put(name, ichi);
            }
            if(nid.equals(Requirement.getTypeRootQId().substring(1)))
              instR = chi.toString();
          }
        }
      }
      Map<String,String> inst = rootToInstances.get(id);
      if(inst!=null)
        for(String s:inst.keySet()) {
          if(pm.isCanceled()) return;
          recInitInst(db, rootToInstances.get(id).get(s), pm);
        }
      //if (dinst.containsKey(db)) return dinst.get(db);
      if (node == null || instR == null){
        return;
      }
      // return null;
      TreeInstancer ti = loadOrCreateTi(instR, db, null, null);
      
      if (instR == null) RLogger.logDebug("instr null");
      dinst.put(db, ti);
      //return ti;
    } catch (Exception e) {
      RLogger.logError(e);
    }finally{
      writeUnlock();
    }
  }

  private static void recInitInst(TreeDB db, String fid, IProgressMonitor pm) {
    if(pm.isCanceled()) return;
    String rid = getUUID(fid);
    TreeInstancer ti = loadOrCreateTi(rid, db, null, null);
    //TODO look into
    //instantiated trees are also tends to be non-revisioned one
    //give parent revision to it for sure
    TreeDB instTree = TreeDB.loadTreeInstance(null,null, db.revision, db.getTracker(), ti, new DefaultUUIDGenerator(), db.getLevelOfAbstraction() + 1, db);
    instTree.getCheckerContainer().attachGuiListener(instTree);
    //TODO check for container events
    //TODO also add after load?
    Map<String,String> inst = rootToInstances.get(fid);
    if(inst!=null)
      for(String s:inst.keySet())
        recInitInst(instTree, rootToInstances.get(fid).get(s), pm);
  }

  // TODO bottleneck
  public static Map<UUID, Set<VirtualNode>> reuses = new HashMap<>();
  public static Map<VirtualNode, UUID> revreuses = new HashMap<>();

  public static Collection<UUID> getClonesAndVnodes(UUID uuId, TreeDB db) {
    if(db.isVirtual())
      db = Instantiator.getRootTree(db);
    Collection<UUID> cl = getClones(uuId, db);
    if (reuses.containsKey(uuId)) {
      for (VirtualNode vn : reuses.get(uuId)) cl.add(vn.getUUId());
    }
    cl.remove(uuId);
    return cl;
    // was reused, clear vnodes
  }

  public static Collection<UUID> getClones(UUID uuId, TreeDB db2) {
    // if(!db.isVirtual())//TODO look into
    // db = getVirtualTreeDB(db,DEFAULT_INSTANCE_NAME);
    if(db2.isVirtual())
      db2 = Instantiator.getRootTree(db2);
    HashSet<UUID> cl = new HashSet<>();
    cl.addAll(TreeInstancer.getClones(db2, db2.getStorage(), uuId, null));
    return cl;
  }

  private static boolean isCloneEquals(TreeDB db, UUID uid, UUID what) {
    Set<VirtualNode> ruses = reuses.get(uid);
    if (ruses != null) for (VirtualNode v : ruses) if (v.getUUId().equals(what)) return true;
    if (uid == null) return false;
    if (uid.equals(what)) return true;
    Object cpv =
        AttributeStorage.getAttributeDefValue(
            db, db.getStorage().getAttributes(uid), uid, TreeNode.ATTR_CLONE_PARENT);
    if (cpv != null) {
      String cp = cpv.toString();
      if (cp.equals(uid.toString())) return false; // selfcloneparent
      return isCloneEquals(db, UUID.fromString(cp), what);
    }
    return false;
  }

  public static TreeNode getChildOrClone(TreeDB db, TreeNode node, UUID fromString) {
    List<UUID> childs = node.getActiveWorkChildrenUUIDs();
    if (childs != null)
      for (UUID uid : childs) if (isCloneEquals(db, uid, fromString)) return db.getNode(uid);
    return null;
  }

  protected static boolean isVRoot(TreeNode node) {
    //Map<String, AttributeDefinition> attrs = node.getAttributes();
    //if (attrs != null) {
      Attribute la = node.attrs().getAttribute(TreeNode.ATTR_VIRT_STATUS);
      if (la != null && la.getValue() != null) {
        Object o = la.getValue();
        if (o != null && o.equals(node.getSUUId()))
          return true;
      }
    //}
    return false;
  }

  private static Map<TreeDB, TreeInstancer> dinst = new HashMap<>();

  public static TreeInstancer getVirtualStorage(
      TreeDB pdb, String name){//, Map<String, Object> initpar, final TreeInstancer oldti) {
    TreeDB db = getExistVirtualTreeDBByInstName(pdb, name, name);//TODO pname?
    if (db != null){
      TreeInstancer ti = gatherVirtualStorage(db);
      if(ti == null)
        ti = loadOrCreateTi(db.getRootNodeSUUID(), db, null, null);
      return ti;
    }
    return null;
  }

  public static TreeInstancer gatherVirtualStorage(TreeDB rdb) {
    if (rdb != null){
      if(rdb.isVirtual())
        return rdb.getVStorage();
      if(!rdb.isVirtual())
        for(TreeDB db:dinst.keySet())
          if(db!=null && db.getLocalName()!=null && db.getLocalName().equals(rdb.getLocalName()))
            return dinst.get(db);
    }
    return null;
  }

  public static void removeInstance(TreeDB instTree, String iroot) {
    writeLock();
    //TODO actual delete?
    
    try{
      String iiroot = getId(iroot, instTree);
      TreeDB droot = iDBToRootDB.get(instTree);
      instTree.close();
      // TreeDB rootDB = getRootTree(instTree);
      rootToDBMap.remove(iiroot);
      iDBToRootDB.remove(instTree);
      rootToInstances.remove(iiroot);
      // TODO rec
      String torem = null;
      if (droot != null) {
        instTree.getStorage().deleteStorage();
        String drid = getId(droot.getRootNodeSUUID(), droot);
        if (rootToInstances.get(drid) != null) {
          for (String s : rootToInstances.get(drid).keySet())
            if (s != null && rootToInstances.get(drid).get(s).equals(iiroot)) {
              torem = s;
  
              break;
            }
          if (torem != null) rootToInstances.get(drid).remove(torem);
        }
      }
    }
    finally{
      writeUnlock();
    }
  }

  private static void writeLock() {
    try {
      if(!wl.tryLock(10,TimeUnit.SECONDS)){
        wl.lock();
      }
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
  }
  private static void writeUnlock() {
    wl.unlock();
  }


  public static void readLock() {
    rl.lock();
  }
  public static void readUnlock() {
    rl.unlock();
  }
  public static List<TreeNode> getClones(TreeDB db, NodeDesc node) {
    readLock();
    try{
      TreeDB vdb = db;
      if(db.isVirtual())
        vdb = getRootTree(db);
      Collection<UUID> uids = getClones(node.getUUId(), vdb);
      List<TreeNode> nodes = new LinkedList<>();
      for (UUID uid : uids) {
        try {
          TreeNode nde = vdb.getCachedNodeOrVirt(uid);
          if (nde != null) nodes.add(nde);
        } catch (NodeNotFoundException e) {
        }
      }
      return nodes;
    }finally{
      readUnlock();
    }
  }

  public static TreeDB getVirtualTreeDB(TreeInstancer treeInstancer) {
    readLock();
    try{
      for (String s : rootToTI.keySet())
        if (rootToTI.get(s) != null
            && rootToTI.get(s).getRootNode().equals(treeInstancer.getRootNode())) // possible
          // source
          // of
          // misstargeting
          // whene
          // there
          // are
          // clones
          // of
          // projects
          return rootToDBMap.get(s);
      // getId(treeInstancer.getRootNode(), tree)
      // TreeDB db = rootToDBMap.get();
      return null;
    }finally{
      readUnlock();
    }
  }
  public static boolean isVRoot(TreeDB db, UUID uuid, Map<String, Attribute> attrs) {
    if (iDBToRootDB.containsKey(db)) return true;
    Object o = AttributeStorage.getAttributeValue(db, attrs, uuid, TreeNode.ATTR_VIRT_STATUS);
    if (o != null && uuid.equals(o)) return true;
    return false;
  }
  public static boolean isDefVRoot(TreeDB db, UUID uuid, Map<String, AttributeDefinition> attrs) {
    if (iDBToRootDB.containsKey(db)) return true;
    Object o = AttributeStorage.getAttributeDefValue(db, attrs, uuid, TreeNode.ATTR_VIRT_STATUS);
    if (o != null && uuid.equals(o)) return true;
    return false;
  }
  public static Collection<? extends TreeDB> getVirtualTreeDBs(TreeDB treeDB) {
    //initIfNeeded(treeDB);
    readLock();
    try{
      Set<TreeDB> vtrees = new HashSet<>();
      String iroot = getId(treeDB.getRootNodeSUUID(), treeDB);
      
      Map<String, String> insts = rootToInstances.get(iroot);
      if (insts != null) {
        for (String uid : insts.values())
          if (rootToDBMap.containsKey(uid)) {
            vtrees.add(rootToDBMap.get(uid));
            vtrees.addAll(getVirtualTreeDBs(rootToDBMap.get(uid)));
          }
      }
      return vtrees;
    }
    finally{
      readUnlock();
    }
  }
  public static TreeDB getExistVirtualTreeDBByRoot(TreeDB db, String name){
    if (db == null) return null;
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if (tsm.getState(db.getLocalName(), tsm.getRevisionInfo(db)) == TreeState.NOT_LOADED || db.getStorage() == null) return null;
    readLock();
    try{
      if(rootToDBMap.containsKey(name))
        return rootToDBMap.get(name);
    }finally{
      readUnlock();
    }
    return null;
  }
  public static TreeDB getExistVirtualTreeDBByInstName(TreeDB db, String lname, String pname){
    if(lname.equals(DEFAULT_INSTANCE_NAME)||pname.equals(DEFAULT_INSTANCE_NAME))
      return db;
    if (db == null) return null;
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if (tsm.getState(db.getLocalName(), tsm.getRevisionInfo(db)) == TreeState.NOT_LOADED || db.getStorage() == null) return null;
    readLock();
    try{
      String uroot = getId(db.getRootNodeSUUID(), db);
      Map<String,String> map = rootToInstances.get(uroot);
      if(map == null)
        return null;
      if(map.containsKey(pname))
        return rootToDBMap.get(map.get(pname));
    }finally{
      readUnlock();
    }
    return null;
  }
//init tree from 0
  public static TreeDB initVirtualTreeDB(
      TreeDB db, String name, Object object, Map<String, Object> initPar,boolean temp) {
    if (db == null) return null;
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if (tsm.getState(db.getLocalName(), tsm.getRevisionInfo(db)) == TreeState.NOT_LOADED  || db.getStorage() == null) return null;
    String uroot = getId(db.getRootNodeSUUID(), db);
    if (uroot == null) return null;
    if (name == null || name.equals(DEFAULT_INSTANCE_NAME)) return db;
    if (db.getStorage().getLocalName().equals(name)) throw new RuntimeException("Name is already used!");//TODO wtf?
    TreeDB root = getRootTree(db, TreeDB.DEFAULT_DEPTH);
    writeLock();
    try{
        String instR = null;
        Map<String,String> map = rootToInstances.get(uroot);
        if(map == null)
          map = new HashMap<String, String>();
        UUID instRU = null;
        String sinstRU = null;
        //boolean ld = false;
        if (!instLoading.contains(db)) {
          instLoading.add(db);
          //initIfNeeded(root);
          TreeNode node = root.getRootNode();
          if (node == null) return null; // loading
          TreeNode
            insts = root.getRootNode().findWorkChild("Instances");
          if(insts == null)
            insts = CreateNode.call(root.getRootNode(),InstancesFolder.TYPE_NAME,"Instances");
          
//          for (UUID uid : root.getStorage().getChildren(root.getStorage().getRootNode()))
//            if (root.getStorage().getNodeId(uid, true).equals("Instances")) {
//              insts = uid;
//              break;
//            }
        //  TreeNode insts = node.findWorkChild("Instances");
//          if (insts == null) insts = node.createChild("Instances", InstancesFolder.TYPE_NAME).getUUId();
//          for (UUID uid : insts.getActiveWorkChildrenUUIDs()) {
//            if (root.isNodeExist(uid)) {
//              Map<String, AttributeDefinition> attrs = root.getStorage().getAttributes(uid);
//              AttributeDefinition temp = attrs.get(INST_NAME);
//              String iname = null;
//              if (temp != null && temp.getRawValue() != null) {
//                Object o = temp.getRawValue().getValue();
//                if (o != null) iname = o.toString();
//              }
//              if (iname != null && iname.equals(name)
//                  || root.getStorage().getNodeId(uid, true).equals(name)) {
//                instR = getId(uid, db);
//                break;
//              }
//            }
//          }
          //if (instR == null) {
          //{  
            Map<String, AttributeDefinition> attrs = new HashMap<>();
            // inst =
            // root.getNode(insts).createChild(nme+name,InstancesFolder.TYPE_NAME);
            instRU = UUID.randomUUID();
            sinstRU = instRU.toString();
            instR = getId(sinstRU, db);
            // make it silent
            AttributeDefinition l =
                new AttributeDefinition(instRU, TreeNode.ATTR_VIRT_STATUS, AttributeAvailability.LOCAL,
                    new ConstValueProvider(AttributeType.STRING, instRU.toString()),Flag.HIDDEN);
            attrs.put(TreeNode.ATTR_VIRT_STATUS, l);
            l = new AttributeDefinition(instRU, INST_NAME, AttributeAvailability.LOCAL,
                new ConstValueProvider(AttributeType.STRING, name),Flag.HIDDEN);
            attrs.put(INST_NAME, l);
            
            attrs.put(ATTR_TEMP, new AttributeDefinition(instRU, ATTR_TEMP, 
                new ConstValueProvider(AttributeType.BOOL, true),Flag.HIDDEN));
            l = new AttributeDefinition(instRU, PARENT_ROOT, AttributeAvailability.LOCAL,
                new ConstValueProvider(AttributeType.STRING, uroot.toString()),Flag.HIDDEN);
            attrs.put(PARENT_ROOT, l);
            l = new AttributeDefinition(instRU, TreeNode.ATTR_TYPE, AttributeAvailability.LOCAL,
                new ConstValueProvider(AttributeType.STRING, InstancesFolder.TYPE_NAME),Flag.HIDDEN);
            attrs.put(TreeNode.ATTR_TYPE, l);
            if (initPar != null) {
              // attrs = new HashMap<String,Attribute>();
              for (String s : initPar.keySet()) {
                if (!s.startsWith("_")) {
                  AttributeDefinition a =
                      new AttributeDefinition(instRU,s,AttributeAvailability.SUBTREE,new ConstValueProvider(AttributeType.STRING, initPar.get(s).toString()),AttributeDefinition.detectFlags(s));
    //                  new Attribute(
    //                      Version.getUnset(),
    //                      instRU,
    //                      AttributeType.STRING,
    //                      s,
    //                      initPar.get(s).toString());
                 // a.setAvailability(AttributeAvailability.SUBTREE);
                  attrs.put(s, a);
                }
              }
            }
            TreeNode old = insts.findWorkChild(name);
            if(old!=null)
              old.delete();//TODO mb more complex
            if (!rootToDBMap.containsKey(uroot)) rootToDBMap.put(uroot, db);
            map.put(name, instR);
            rootToInstances.put(uroot, map);
            // TreeInstancer ti = loadOrCreateTi(instR, fdb, prevInst, initPar);
            instLoading.remove(db);
            insts.createChild(insts.getUUId(), instRU, name, null, attrs, OFlag.NONE);
            // root.getDeltaTarget().deleted(uuid, true);
            // root.removeCache(inst.getUUId());
          //}
          // iRootToDRoot.put(instR,uroot);
          //ld = true;
        }
       // if (instLoading.contains(db)) return null;
        TreeDB instTree = rootToDBMap.get(instR);
        //if (ld || instTree == null && instR != null) {
        final UUID finstR = instRU;
        TreeInstancer ti = loadOrCreateTi(sinstRU, db, null, initPar);
        
        instTree = TreeDB.loadTreeInstance(null, null,db.revision, db.getTracker(), ti, new DefaultUUIDGenerator(), db.getLevelOfAbstraction() + 1, db);
        TreeStateManager.getOne(db.getTracker()).setVirtualState(instTree, true);
        iDBToRootDB.put(instTree, db);
        // root.replaceCache(rt.getUUId(),instTree.getRootNode());
        List<TreeNode> reqs = db.getRootNode().getWorkChildren(Requirement.TYPE_NAME, true, true, true);
        if (reqs == null || reqs.size() == 0) {
          reqs = db.getRootNode().getWorkChildren(TextNode.TYPE_NAME, true, true, true);
          if (reqs == null || reqs.size() == 0)
            reqs = db.getRootNode().getWorkChildren(null, true, true, true); // not
          // recommended
        }
  
        if (instR == null) RLogger.logDebug("instr null");
        else rootToDBMap.put(instR, instTree);
        for (TreeNode rootreq : reqs) {
          if (rootreq.getType().equals(RestrictedFolder.TYPE_NAME)
              || rootreq.getType().equals(InstancesFolder.TYPE_NAME)) continue;
          Collection<UUID> childs = ti.getChildren(finstR);
          UUID iroot = null;
          if (childs != null && childs.iterator().hasNext()) iroot = childs.iterator().next();
          TreeNode nroot = null;
          if (iroot == null) { // rec
            iroot = UUID.randomUUID();
            //instTree.setIsLoaded();
            iroot = ti.cloneUUID(instTree, iroot, rootreq, finstR, rootreq.getId());
            try {
              nroot = instTree.getNode(iroot, TreeDB.DEFAULT_DEPTH, true);
            } catch (NodeNotFoundException e) {
              RLogger.logError(e);
            }
            //instTree.updateNodeChildren(nroot);
          } else {
            // nroot = instTree.getNode(iroot,false,false,false);
            try {
              nroot = instTree.getNode(iroot, TreeDB.DEFAULT_DEPTH, false);
            } catch (NodeNotFoundException e) {
              RLogger.logError(e);
            }
          }
        }
  
        // instTree.getNode(iroot,false,false,false);
        // if(up){
        // Stack<UUID>stack= new Stack<UUID>();
        // stack.add(iroot);
        // while(!stack.isEmpty())
        // {
        // UUID uid = stack.pop();
        // TreeNode node = instTree.getNode(uid);
        // if(Version.lessThen(nroot.getCPVersion(),rootreq.getVersion()))
        // nroot.updateCPChildren();
        // stack.addAll(node.getRawChildrenUUIDs());
        // RLogger.logInfo(node.getQualifiedId()+"
        // "+node.getUserVisibleName()+" "+Requirement.getFullName(node,
        // instTree));
        // //if(!node.getVersion().equals(node.getCPVersion()))
        // //db.updateNodeChildren(node);
        // }
        // }
        // instTree.addListener(getOldToNewDBListener(db,instTree));
        instTree.addListener(delListener(db, instTree));
        instTree.getCheckerContainer().attachGuiListener(instTree);
        //instTree.setIsLoaded();
        //}
     // Свойства тут не переустанавливаются
        // Дополнительно - в req explorer не отображается поддерево требований
        // для отчета, но работает
        if (initPar != null) {
          boolean changes = false;
          Set<String> torem = new HashSet<>();
          for (Entry<String, Attribute> en : instTree.getRootNode().attrs().getLocalAttributes().entrySet()) {
            if (!en.getKey().startsWith("_")) {
              Attribute a = en.getValue();
              if (instTree.getRootNode() != null
                  && a != null
                  && a.getAvailability() == AttributeAvailability.SUBTREE
                  && a.getOrigin().equals(instTree.getRootNodeUUID())) {
                torem.add(en.getKey());
                changes = true;
              }
            }
          }
          for (Entry<String, Object> en : initPar.entrySet())
            if (!en.getKey().startsWith("_")) {
              torem.remove(en.getKey());
              changes = true;
              TreeNode roota = instTree.getRootNode();
              AttributeDefinition ad = new AttributeDefinition(roota.getUUId(), en.getKey(), AttributeAvailability.SUBTREE,
                    AttributeDefinition.detectValueProvider(en.getKey(), AttributeType.STRING,
                      en.getValue().toString(), TempFolderFilesProvider.EMPTY),
                  0);
              roota.attrs().setAttribute(ad);
            }
          for (String s : torem) instTree.getRootNode().attrs().removeAttribute(s);
          if (changes) instTree.getRootNode().commitAttributes();
        }

        if (instR != null) return instTree;
        return null;
      }
      catch(Exception e){
        RLogger.logError("init instance error: "+CoreUtils.getTrace(e));
      }
      finally{
        writeUnlock();
      }
    return null;
    }
 
    public static void removeTempInstances(TreeDB db, IProgressMonitor pm) {
      TreeNode node = db.getRootNode();
      if(pm.isCanceled() || node == null) return;
      for(UUID nde:node.getWorkChildrenUUIDs()){
        if(pm.isCanceled()) return;
        String nid = db.getStorage().getNodeId(nde);
        if(nid!=null && nid.equals("Instances")){
          node = db.getNode(nde,0,true);
          for(TreeNode chi:node.getWorkChildren())
          {
            if(chi.attrs().getBooleanAttributeValue(ATTR_TEMP,false)){
              if(pm.isCanceled()) return;
	          removeInstance(db, chi.getSUUId());
	          chi.delete();
	        }
	      }
          if(pm.isCanceled()) return;
	      if(node.sizeWorkChildren()==0)
            node.delete(OFlag.NONE);
        }
      }
    }
  }
