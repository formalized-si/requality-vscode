/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.core.instancer;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefToMerge;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.MergeParams;
import com.unitesk.requality.core.attribute.Version;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.AbstractTreeStorage;
import com.unitesk.requality.core.storage.CacheInitializationStage;
import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.IStorageSource;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.ICacheStorageProvider;
import com.unitesk.requality.tool.CoreUtils;

public class TreeInstancer extends AbstractTreeStorage {
  public static final String ATTR_CLONE_ID = "_cloneid";
  private IStorageSource baseStorageF;
  private TreeDB srcTree;
  private String name;//TODO mb add local and project
  // public static String
  // private Map<UUID, Map<String,Attribute>> changedAttribute = new
  // HashMap<UUID, Map<String,Attribute>>();
  // private Map<UUID, Map<String,Attribute>> generatedAttribute = new
  // HashMap<UUID, Map<String,Attribute>>();

  @Override
  public Map<String, Version> getVersions(UUID uuid) {
    return getBaseStorage().getVersions(uuid);
  }
  

  @Override
  public void saveVersions(UUID uuid, ChangesSet<Version> changes, byte flag) {
    getBaseStorage().saveVersions(uuid, changes, flag);
  }

  // TODO toprivate
  // public Map<UUID,UUID> parentReplace = new HashMap<UUID, UUID>();
  // public Map<UUID,Set<UUID>> revParentReplace = new HashMap<UUID,
  // Set<UUID>>();
  @Override
  public ITreeStorage getBaseStorage() {
    return baseStorageF.getBaseStorage();
  }

//  private static Map<String, Object> attributesToObjects(
//      TreeNode node, Map<String, Attribute> map) {
//    Map<String, Object> vals = new HashMap<String, Object>();
//    for (String key : map.keySet()) {
//      Attribute sel = map.get(key);
//      vals.put(sel.getKey(), sel.getValue(node.attrs()));
//    }
//    return vals;
//  }

  public TreeInstancer(
      final TreeDB db, String ci, String instName, IStorageSource st) {
    this.srcTree = db;
    this.baseStorageF = st;
    this.name = instName;
    this.cloneid = ci;
    loadChanges();
    if(instName.equals("/") || instName.equals(Instantiator.DEFAULT_INSTANCE_NAME))
      cs = st.getBaseStorage().getCacheStorage();
	else
      try {
        initCaches(this, CacheInitializationStage.STORAGE_INIT, getMonitor());
      } catch (Exception e) {
        RLogger.logError(e);
      }//TODO CacheInitStage.FULL?
    // initRoot(srcTree);
  }

  public TreeInstancer(
      final TreeDB db,
      String ci,
      String instName,
      IStorageSource st,
      TreeInstancer ti) {
    this(db, ci, instName, st);
    // readOnly = true;
    // this.baseStorageF = st;
    // this.initalAttribute = new HashMap<String, Attribute>();
    // this.name = instName;
    // if(map!=null)
    // for(String s:map.keySet())
    // initalAttribute.put(s, new Attribute(Version.getZero(),null,
    // AttributeType.STRING, s, map.get(s).toString()));
    // this.srcTree = db;
    // this.cloneid = ci;
    // loadChanges();
    // generatedAttribute.clear();
    // changedAttribute.clear();
    //
    // for(UUID uid:ti.changedAttribute.keySet())
    // changedAttribute.put(uid, new HashMap<String,
    // Attribute>(ti.changedAttribute.get(uid)));
    // for(UUID uid:ti.generatedAttribute.keySet())
    // generatedAttribute.put(uid, new HashMap<String,
    // Attribute>(ti.generatedAttribute.get(uid)));
    // cloneParent = new HashMap<UUID, UUID>(ti.cloneParent);
    // cloneParentChildren = new HashMap<UUID, Set<UUID>>();
    // for(UUID uid:ti.cloneParentChildren.keySet())
    // cloneParentChildren.put(uid, new
    // HashSet<UUID>(ti.cloneParentChildren.get(uid)));
    // parentReplace = new HashMap<UUID, UUID>(ti.parentReplace);
    // initRoot(srcTree);
  }

  private String cloneid = null;
  private UUID cloneuid = null;

  @Override
  public UUID getRootNode() {
    if(cloneuid == null)
      if(cloneid!=null)
        cloneuid = UUID.fromString(cloneid);
    return cloneuid;
    // return cloneUUID(null,getBaseStorage().getRootNode(),null,null);
  }

  // public UUID getStoredParent(UUID uuid){
  // if(parentReplace.containsKey(uuid))
  // return parentReplace.get(uuid);
  // //if(oldti!=null)
  // //return oldti.getParent(uuid);
  // return null;
  // }
  @Override
  public UUID getParent(UUID uuid) {
    UUID uid = null;
    // UUID uid = parentReplace.get(uuid);
    uid = getBaseStorage().getParent(uuid);
    // if(uid == null)
    // uid = getStoredParent(uuid);
    if (uid != null) return uid;
    return uid;
  }

  public String getErrors() {
    UUID root = getRootNode();
    Map<String, Object> attr = new HashMap<>();
    Map<String, AttributeDefinition> map = getAttributes(root);
    AttributeDefinition err = map.get(TreeNode.ATTR_ERROR);
    if (err != null) {
     
      Object r = err.getValue(null);
      if (r != null) return (String) r;
    }
    return "";
  }
  // @Override
  // public Collection<UUID> getChildren(UUID uuid,boolean raw) {
  // if(uuid==null)
  // return Collections.EMPTY_LIST;
  // createIfNeeded(uuid);
  // UUID baseuid = uuid;
  // //todo handle clones
  //// if(oldti!=null)
  //// return oldti.getChildren(uuid, raw);//TODO fix
  //
  // return result;
  // }

  public static Map<String, Attribute> toMap(Collection<Attribute> cl) {
    if (cl == null) return null;
    Map<String, Attribute> map = new HashMap<>();
    for (Attribute a : cl) map.put(a.getKey(), a);
    return map;
  }

  private HashMap<String, AttributeDefinition> formCloneAttributes(
      TreeDB db,
      UUID newid,
      String id,
      UUID cloneparent,
      UUID parent,
      Map<String, AttributeDefinition> attrs) {
    HashMap<String, AttributeDefinition> ret = new HashMap<>();
//    Map<String, AttributeDefinition> cpa = null;
//    Map<String, AttributeDefinition> pa = null;
    AttributeDefinition temp =
        (new AttributeDefinition(newid, ATTR_CLONE_ID, AttributeAvailability.LOCAL,
            new ConstValueProvider(AttributeType.STRING, id),Flag.HIDDEN));
    ret.put(ATTR_CLONE_ID, temp);
    if (cloneparent != null) {
      //TODO rethink clones on vers
      temp = new AttributeDefinition(newid, TreeNode.ATTR_CLONE_PARENT,
          AttributeAvailability.LOCAL,
          new ReferenceValueProvider(
              AttributeType.REFERENCE, cloneparent.toString())
          , Flag.HIDDEN);
//          (new Attribute(
//              Version.getUnset(),
//              newid,
//              AttributeType.REFERENCE,
//              TreeNode.ATTR_CLONE_PARENT,
//              cloneparent.toString()));
      ret.put(TreeNode.ATTR_CLONE_PARENT, temp);
    }
    if (attrs != null)
      for (String s : attrs.keySet()) {
        AttributeDefinition toa = null;
        AttributeDefinition a = attrs.get(s);
        if (a != null) {
          //if (a.getOrigin() == null) toa = a.copy(newid, newid));
          toa = (a);
        }
        if (!ret.containsKey(s)) ret.put(s, toa);
        else
          ret.put(
              s,
              AttributeStorage.mergeAttrDefs(
                  db,
                  newid,
                  new AttributeDefToMerge(toa, MergeParams.ALL, false),
                  new AttributeDefToMerge(ret.get(s), MergeParams.ALL, false)));
      }
    ret.remove(AttributesVersioningRule.ATTR_NODE_VERSION);//reset version
    temp =
        new AttributeDefinition(
            newid,
            TreeNode.ATTR_VIRT_STATUS,
            AttributeAvailability.LOCAL,
            new ConstValueProvider(AttributeType.STRING, getRootNode().toString()),
            Flag.HIDDEN);
    ret.put(TreeNode.ATTR_VIRT_STATUS, temp);

    Set<String> allkeys = new HashSet<>();
    allkeys.addAll(ret.keySet());
    // if(pa!=null)
    // allkeys.addAll(pa.keySet());
    // if(cpa!=null)
    // allkeys.addAll(cpa.keySet());
//    for (String s : allkeys) {
      // LinkedList<Attribute> la = new LinkedList<Attribute>();
      // if(initalAttribute!=null && initalAttribute.containsKey(s))
      // la.add(initalAttribute.get(s));
//      ret.put(
//          s,
//          AttributeStorage.mergeAttrDefs(
//              db,
//              newid,
//              // new
//              // ListToMerge(s.startsWith("_")?null:la,MergeParams.ALL,false),//lowest
//              // prior
////              new AttributeDefToMerge(
////                  !s.startsWith("_%") && cpa != null ? cpa.get(s) : null, MergeParams.ALL, true),
////              new AttributeDefToMerge(
////                  !s.startsWith("_%") && pa != null ? pa.get(s) : null, MergeParams.NOTLOCAL, true),
//              new AttributeDefToMerge(ret.get(s), MergeParams.ALLHP, false)));
//    }
    return ret;
  }

  private String getType(Map<String, AttributeDefinition> attrs) {
    // TODO Auto-generated method stub
    return null;
  }

  public UUID cloneUUID(TreeDB db, UUID newid, TreeNode cloneParent, UUID master, String id) {
    return cloneUUID(db, newid, cloneParent, master, id, null, null);
  }

  public UUID cloneUUID(
      TreeDB db,
      UUID newid,
      TreeNode cloneParent,
      UUID master,
      String id,
      Collection<UUID> childs,
      Map<String, AttributeDefinition> addattrs) {
    // if(oldti!=null)
    // return null;//TODO fix
    if (id == null) id = "01";
    // самое тормознутое
    // if(addattrs==null && cloneParent!=null)
    // addattrs = AttributeStorage.cloneMap(getAttributes(cloneParent),
    // true,newid);
    if (newid == null) {
      newid = UUID.randomUUID();
    }
    Map<String, UUID> occupiedIds = new HashMap<>();
    if (childs == null) {
      if (canLoad(master)) childs = getChildren(master); // TODO look
      else childs = getBaseStorage().getChildren(master);
    }
    for (UUID pid : childs) {
      if (isSame(pid, cloneParent.getUUId(), master, id)) return pid;
      occupiedIds.put(getNodeId(pid), pid);
    }
    if (canLoad(newid)) occupiedIds.remove(getNodeId(newid));

    // addCloneParent(newid,cloneParent);
    // addClone(cloneParent,newid);
    Map<String, AttributeDefinition> attrs =
        formCloneAttributes(db, newid, id, cloneParent.getUUId(), master, addattrs);
    if (!canBaseLoad(newid)) {
      // TreeDB db = srcTree;
      // if(!db.isVirtual())
      // db = Instantiator.getVirtualTreeDB(this);
      // IStorageDeltaListener del = getBaseStorage().getDeltaTarget();
      try {
        // addVirtChild(masteruid,newid);
        // cloneA.put(newid, attrs);
        // cloneK.put(newid, id);
        //
        
        createNode(NodeDescSet.fromNodeDesc(new NodeDesc( newid, master, id, attrs, true),true), null, OFlag.NONE);//TODO look
        //TODO check version to be updated here
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        // getBaseStorage().setDeltaTarget(del);
      }
      // if(canLoad(newNode.getParentUUId(true)))
      // db.updateNodeChildren(newNode.getParent(true));
    } else { // TODO check
      // IStorageDeltaListener del = getBaseStorage().getDeltaTarget();
      // try{
      // //getBaseStorage().setDeltaTarget(srcTree.getNullDeltaListener());
      // Map<String,Attribute> old = AttributeStorage.cloneMap(
      // getCachedOrBaseAttributes(newid),true,newid);
      // if(old == null)
      // old = new HashMap<String,Attribute>();
      // int num = 0;
      // if(occupiedIds.containsKey(id)){
      // while(occupiedIds.containsKey(id+num))
      // num++;
      // id = id+num;
      // //changeNodeId(occupiedIds.get(id), rid);
      // }
      // //changeNodeId(newid, id);//TODO look into
      // for(String s:attrs.keySet())
      // old.put(s, attrs.get(s));
      // saveAttributes(newid, old.keySet(), old,false);
      // }catch(Exception e){
      // e.printStackTrace();
      // }
      // finally{
      // //getBaseStorage().setDeltaTarget(del);
      // }
    }
    return newid;
  }

  // @SuppressWarnings("unchecked")
  // private static void addClone(TreeNode master, UUID newid) {
  // Set<TreeNode> a = master.getRevertLinks(ATTR_CLONE_PARENT);
  // master.attrs().saveAttributes();
  // }
  private boolean isSame(UUID pid, UUID cloneParent2, UUID masteruid, String id) {
    Map<String, AttributeDefinition> atrs = getAttributes(pid);
    AttributeDefinition ca = atrs.get(ATTR_CLONE_ID);
    Object cloneid = null;
    if (ca != null && ca.getRawValue() != null) cloneid = ca.getRawValue().getValue();
    if (cloneid != null && cloneid.equals(id) || cloneid == null && id == null) {
      ca = atrs.get(TreeNode.ATTR_CLONE_PARENT);
      Object cp = null;
      if (ca != null && ca.getRawValue() != null) cp = ca.getRawValue().getValue();
      if (cp != null && cp.equals(cloneParent2.toString()) || cp == null && cloneParent2 == null) {
        if (getParent(pid) != null && getParent(pid).equals(masteruid)
            || getParent(pid) == null && masteruid == null) return true;
      }
    }
    return false;
  }

  public String evaluatePredicateString(UUID id, String predicate) {
    if (predicate == null) predicate = getPredicate(id);
    if (predicate == null || predicate.length() == 0) return "";
    UUID sid = id;
    String contextResult = "";
    try {
      ScriptEngine engine = CoreUtils.getJSEngine();
      ScriptContext context = engine.getContext();
      // invalid return
      Object res = engine.eval(predicate, context);
      contextResult = res.toString();
    } catch (Exception e) {
      return e.toString();
    } finally {
    }
    return contextResult;
  }

  public String checkPredicate(UUID id) {
    return checkPredicate(id, null);
  }

  public String checkPredicate(UUID id, String predicate) {
    if (predicate == null) predicate = getPredicate(id);
    String result = "1";
    if (predicate == null || predicate.equals("")) return result;
    // TODO proper db
    TreeDB db = Instantiator.getVirtualTreeDB(this);
    // if(!db.isVirtual())
    // db = VirtualNode.getVirtualTreeDB(db);
    if (db == null) db = srcTree;

    TreeNode node = db.getNode(id);
    result = node.attrs().evaluatePredicateString(predicate,AttributeStorage.formPredDeps(predicate,node.attrs()));
    return result;
  }

  public String getPredicate(UUID id) {
    TreeDB db = srcTree;
    if (!db.isVirtual()) db = Instantiator.getExistVirtualTreeDBByInstName(db, getLocalName(),getProjectName()); // treeDB for
    // current inst
    try {
      TreeNode node = db.getNode(id);
      if (node != null) return node.getPredicate();
    } catch (Exception e) {
    }
    return null;
  }
//
//  private Map<String, Object> getAttributesMap(TreeNode node) {
//    return attributesToObjects(node, node.attrs().getAvailableAttributes());
//  }

  private String evaluatePredicateWithErrors(String predicate, Map<String, Object> map) {
    if (predicate == null || predicate.length() == 0) return "1";
    String error = "";
    Boolean contextResult = true;
    try {
      ScriptEngine engine = CoreUtils.getJSEngine();
      ScriptContext context = engine.getContext();
      Object o = engine.eval(predicate, context);
      if (o instanceof Boolean) contextResult = (Boolean) o;
      else {
        error += "'" + predicate + "' returns undefined value";
      }
    } catch (Exception e) {
      error += "'" + predicate + "' causes an error: " + e.getMessage();
    } finally {
    }
    return (contextResult ? "1" : "0") + error;
  }

  // private Scriptable createScope(Context context, Map<String, Object>
  // attributes) {
  // Scriptable topScope = ScriptRuntime.initStandardObjects(context, null,
  // true);
  // Scriptable scope = new NativeObject();
  // scope.setParentScope( null );
  // scope.setPrototype( topScope );
  //
  // for(String attrName: attributes.keySet() ) {
  // Object value = attributes.get(attrName);
  // if(!value.getClass().isArray())
  // value = value.toString();
  // Object obj = context.getWrapFactory().wrap( context, scope, value,
  // value.getClass() );
  // scope.put( attrName, scope, obj );
  // }
  //
  // return scope;
  // }
  public void pseudoCreateNode(
      final UUID parentUUId, UUID uuid, String id, String templ, Map<String, AttributeDefinition> attrs, byte flag) {
    if (!canBaseLoad(uuid)) { // TODO tests
      IStorageDeltaListener delta = getBaseStorage().getDeltaTarget();
      try {
        getBaseStorage().setDeltaTarget(srcTree.getNullDeltaListener());
        if (parentUUId != null && !canBaseLoad(parentUUId)) {
          // Map<String,Attribute> tpmap = new
          // HashMap<String,Attribute>();
          pseudoCreateNode(
              getParent(parentUUId),
              parentUUId,
              getNodeId(parentUUId),
              templ,
              getAttributes(parentUUId), 
              flag);
        }
        if (!canBaseLoad(uuid) && isVirtual(uuid, id, attrs, true)) {
          // creating.add(uuid);
          getBaseStorage().createNode(NodeDescSet.fromNodeDesc(new NodeDesc(uuid,parentUUId, id, attrs, false),true),templ, flag);
          // creating.remove(uuid);
          getChildren(uuid);
        }
      } finally {
        getBaseStorage().setDeltaTarget(delta);
      }
      // cloneA.remove(uuid);
      // cloneK.remove(uuid);
    }
  }

  @Override
  public void createStorageNode(
      NodeDescSet nd, String templ, byte flag) {
    NodeDesc nda = nd.root;
    UUID uuid = nda.getUUId();
    if (!canBaseLoad(uuid)) { // TODO test
      try {
        if (!canBaseLoad(uuid) && isVirtual(nda)) {
          getBaseStorage().createNode(nd, templ, flag);
        }
      } finally {
      }
    }
  }

  public void remove(NodeDescSet node, Set<UUID> washere, byte flag) {
    getBaseStorage().remove(node, flag);
    // getDeltaTarget().deleted(node,Moment.AFTER, true);

  }

  public void remove(UUID uid, byte flag) {
    // TODO support it
    Set<UUID> washere = new HashSet<>();
    NodeDescSet nd = getCloneNodeDescSet(uid);
    if(nd!=null)
      remove(nd, washere, flag);
    return;
    // throw new UnsupportedOperationException();
  }
  private NodeDescSet getCloneNodeDescSet(UUID uid) {
    return NodeDescSet.fromNodeDesc(new NodeDesc(uid, getParent(uid), getNodeId(uid), getAttributes(uid), false),true);//TODO test
  }

  @Override
  public void remove(NodeDescSet node, byte flag) {
    // TODO support it
    Set<UUID> washere = new HashSet<>();
    remove(node, washere, flag);
    return;
    // throw new UnsupportedOperationException();
  }

  @Override
  public String getNodeId(UUID uuid) {
    String id = getBaseStorage().getNodeId(uuid);
    // if(id == null && cloneA.containsKey(uuid))
    // id = cloneK.get(uuid).substring(3);
    if (id == null && hasCloneParent(uuid)) id = getNodeId(getCloneParent(uuid));
    if (id != null) return id;
    return null;
    // return id.substring(4);//vnodes tends to be started from "_v_"
  }

  @Override
  public void changeNodeId(final UUID node, String id, byte flag) {
    // TODO maybe bug
    // stop

    getBaseStorage().changeNodeId(node, id, flag); // TODO maybe bug
    // if(!changedAttribute.containsKey(node))
    // changedAttribute.put(node, new HashMap<String, Attribute>());
    // changedAttribute.get(node).put("_%id", new Attribute(node,
    // AttributeType.STRING,"_%id", id));
    // getDeltaTarget().changed(node ,new HashSet<String>(){{add("_%id");}},
    // true);
  }

  public boolean hasCloneParent(UUID uid) {
    UUID cloneP = getCloneParent(uid);
    return cloneP != null && !uid.equals(cloneP);
  }

  public Map<String, AttributeDefinition> getChangedAttributes(UUID uuid) {
    if (!canBaseLoad(uuid)) return null;
    Map<String, AttributeDefinition> ch = getCachedOrBaseAttributes(uuid);
    if (ch != null) return ch;
    return null;
  }

  public boolean canBaseLoad(UUID uid) {
    ITreeStorage ts = getBaseStorage();
    if (ts.canLoad(uid)) return true;
    if (ts instanceof TreeInstancer) {
      return ((TreeInstancer) ts).canBaseLoad(uid);
    }
    return false;
  }

  public Map<String, AttributeDefinition> getBaseAttributes(UUID uid) {
    ITreeStorage ts = getBaseStorage();
    Map<String, AttributeDefinition> a = ts.getAttributes(uid);
    if (a != null) return a;
    if (ts instanceof TreeInstancer) {
      return ((TreeInstancer) ts).getBaseAttributes(uid);
    }
    return null;
  }

  @Override
  public Map<String, AttributeDefinition> getStorageAttributes(final UUID uuid) {
    // if(cloneA.containsKey(uuid))
    // return cloneA.get(uuid);
    Map<String, AttributeDefinition> map = null;
    if ((canBaseLoad(uuid))) map = getBaseAttributes(uuid);
    if (map == null) {
      UUID clonep = getCloneParent(uuid);
      if (clonep != null && !clonep.equals(uuid) && canBaseLoad(clonep)) {
        map = new HashMap<>(getAttributes(clonep));
        map.put(
            TreeNode.ATTR_VIRT_STATUS,
            new AttributeDefinition(uuid,TreeNode.ATTR_VIRT_STATUS,AttributeAvailability.LOCAL,new ConstValueProvider(AttributeType.STRING, getRootNode().toString()),
                Flag.HIDDEN));
      }
      if (map == null) map = new HashMap<>();
    }
    return map;
  }

  @Override
  public void saveStorageAttributes(UUID node, ChangesSet<Attribute> what, byte flag) {
    saveAttributes(node, what, flag);
  }

  public void saveAttributes(UUID node, ChangesSet<Attribute> changes, byte flag) {
    // if(oldti!=null)
    // return;//TODO fix
    // boolean changed = false;
    // TODO maybe load from base
    // Object[]ch = getChangedAttrs(node,attributes,changes);
    //Map<String, AttributeDefinition> map = attributes; // AttributeStorage.cloneMap(attributes,false);
    ChangesSet<Attribute> changed = new ChangesSet<>(changes);
    // TODO cloneid
    if (!changed.isEmpty()) {
      // changedAttribute.put(node, map);
      if (!canBaseLoad(node) && isVirtual(node)) {
        //TODO mb bug
        createNode(NodeDescSet.fromNodeDesc(new NodeDesc( node,getParent(node), getNodeId(node), 
            AttributeStorage.toDefMap(changed.getNewMap()),false),true),null, flag);
        // getBaseStorage().createNode(getParent(node), node,
        // getNodeId(node), map);//may be wrong
      } else {
        getBaseStorage().saveAttributes(node, changed, flag);
        // getDeltaTarget().changed(node, changed, true);
      }
      // what to do with gen?
      // generatedAttribute.put(node, genmap);
    }
  }

  public void saveChanges() {}

  private void loadChanges() {}

  @Override
  public void moveNodeTo(UUID node, UUID parent, byte flag) {
    // UUID oldparent = getParent(node);
    // if(parent!=null)
    // parentReplace.put(node, parent);
    // else
    // parentReplace.remove(node);
    try {
      getBaseStorage().moveNodeTo(node, parent, flag);
    } catch (Exception e) {
      e.printStackTrace();
      // already made?
    }
    // getDeltaTarget().moved(node, oldparent, parent, true);
    // throw new UnsupportedOperationException();
  }

  @Override
  public String getNodeRawType(UUID uuid) {
    try {
      String s = null;
      s = getBaseStorage().getNodeRawType(uuid);
      if (s != null) return s;
      UUID uid = uuid;
      if (hasCloneParent(uid)) {
        uid = getCloneParent(uid);
        s = getNodeRawType(uid);
        if (s != null) return s;
      }
      return s;
    } catch (Exception e) {
      return null;
    }
  };

  public boolean isVirtual(NodeDesc nd) {
    return isVirtual(nd.getUUId(), nd.getId(), nd.getAttributes(),false);
  }
  
  public boolean isVirtual(UUID uid) {
    return isVirtual(uid, getNodeId(uid), getAttributes(uid), false);
  }

  public Map<String, AttributeDefinition> getBAttributes(UUID uid) {
    if (canBaseLoad(uid)) {
      return getBaseStorage().getAttributes(uid);
    }
    return null;
  }

  public String getVMark(UUID uid, Map<String, AttributeDefinition> attrs) {
    if (attrs == null) {
      AttributeStorage as = AttributeStorage.getCachedStorage(this.srcTree, uid);
      if (as != null) {
        Object a = as.getAttributeValue(TreeNode.ATTR_VIRT_STATUS);
        if (a != null) return a.toString();
        return null;
      }
    }
    if (attrs == null) attrs = getAttributes(uid);
    // if(attrs == null){
    // AttributeStorage as = AttributeStorage.getCachedStorage(uid);
    // if(as!=null)
    // attrs = as.getAvailableAttributes();
    // else
    // attrs = getBAttributes(uid);
    // }
    if (attrs == null) return null;
    if (!attrs.containsKey(TreeNode.ATTR_VIRT_STATUS)) return null;
    AttributeDefinition a = attrs.get(TreeNode.ATTR_VIRT_STATUS);
    if (a == null) return null;
    if (a.getRawValue() == null) return null;
    if (a.getRawValue().getValue() == null) return null;
    return a.getRawValue().getValue().toString();
  }

  public boolean isVirtual(UUID uid, String id, Map<String, AttributeDefinition> attrs, boolean week) {
    if (uid.equals(getRootNode())) return true; // can load root
    String vmark = getVMark(uid, attrs);
    if (vmark != null) return week || vmark.equals(getRootNode().toString());
    return false;
  }

  @Override
  public boolean canLoad(UUID uuid) {
    // if(creating.contains(uuid))
    // return false;
    // if(oldti!=null){
    // //reports mode;
    // if(oldti.canLoad(uuid))
    // return true;
    // }
    if (getRootNode()!=null && getRootNode().equals(uuid)) return true;
    // if(parentReplace.containsKey(uuid)&&
    // cloneid.equals(parentReplace.get(uuid)))
    // return true;
    if (canBaseLoad(uuid))
      return isVirtual(uuid) || Instantiator.isDefVRoot(srcTree, uuid, getAttributes(uuid));
    return false;
    // while(hasCloneParent(uuid))
    // uuid = cloneParent.get(uuid);
    // if(!srcTree.canBaseLoad(uuid)){
    // return changedAttribute.containsKey(uuid);
    // }
    // return true;
  }

  @Override
  public URI getNodeResourcePath(UUID uuId){
    // TODO witthout cp
    // String sp =
    // URI.create(getBaseStorage().getNodeResourcePath(uuId)).toString();
    // if(sp!=null)
    // return sp;
    // //here to be proper images handling
    // virtual nodes(in tree or reports) may have two sets of images -
    // inherited from clone parent
    // and local one. In a case
    boolean base = true;
    if (hasCloneParent(uuId)) {
      base = false;
      uuId = getCloneParent(uuId);
    }
    if (!base) {
      return getNodeResourcePath(uuId);
    }
    return getBaseStorage().getNodeResourcePath(uuId);
  }

  @Override
  public InputStream getResourceContents(UUID uuid, String resName) {
    // TODO witthout cp
    InputStream is;
    is = getBaseStorage().getResourceContents(uuid, resName);
    if (is != null) return is;
    if (hasCloneParent(uuid)) return getResourceContents(getCloneParent(uuid), resName);
    return null;
  }

  @Override
  public void setResourceContents(UUID uuid, String resName, InputStream in, Object data, byte flag) {
    // TODO witthout cp
    getBaseStorage().setResourceContents(uuid, resName, in, data, flag);
    // throw new UnsupportedOperationException();
  }

  @Override
  public boolean isResourceExists(UUID uuid, String resName) {

    // TODO witthout cp
    // while(hasCloneParent(uuid))
    // uuid = cloneParent.get(uuid);
    boolean b = getBaseStorage().isResourceExists(uuid, resName);
    if (!b) {
      if (hasCloneParent(uuid) && !uuid.equals(getCloneParent(uuid))) {
        uuid = getCloneParent(uuid);
        return isResourceExists(uuid, resName);
      }
    }
    return b;
  }

  @Override
  public void deleteResource(UUID uuid, String resName, byte flag) {
    // if(readOnly)
    // return;
    getBaseStorage().deleteResource(uuid, resName, flag);
  }

  private Set<String> getRess(UUID uuid) {
    Set<String> res = getBaseStorage().listResources(uuid);
    if (res != null) return res;
    return Collections.emptySet();
  }

  @Override
  public Set<String> listResources(UUID uuid) {
    Set<String> b = new HashSet<>(getRess(uuid));
    // if(b!=null && b.length>0)
    // return b;
    // if(oldti!=null)
    // return oldti.listResources(uuid);
    if (hasCloneParent(uuid)) b.addAll(getRess(getCloneParent(uuid)));
    return b;
  }

  @Override
  public void createStorage(String lname,String pname) {
    // TODO Auto-generated method stub
  }

  @Override
  public void deleteStorage() {
    cs.dispose();
    // TODO Implement me!
    // throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public String getLocalName() {
    return name;
  }
  
  @Override
  public String getVisibleName() {
    return name;
  }
  @Override
  public String getProjectName() {
    return name;
  }

  public Set<UUID> getClones(TreeDB db, UUID uuId) {
    TreeDB vdb = db;
    if(db.isVirtual())
      vdb = Instantiator.getRootTree(db);
    return getClones(db, vdb.getStorage(), uuId, null);
  }

  public static Set<UUID> getClones(TreeDB db, ITreeStorage st, UUID uuId, UUID uuid2) {
    if (uuId == null) return null;
    Set<UUID> uids = new HashSet<>();
    if (st.canLoad(uuId)) {
      TreeNode cn = db.getCachedNode(uuId);
      AttributeStorage as = cn == null ? null : cn.attrs();
      Map<String, AttributeDefinition> attrs = null;
      if (as != null) attrs = as.getActiveAttributeDefs();
      else attrs = st.getAttributes(uuId);
      
      if (attrs != null && as != null) {//TODO look
        Map<UUID, String> nodes =
          RelManager.getRevertLinks(db, uuId, false, TreeNode.ATTR_CLONE_PARENT);
        if (nodes != null) 
          for (UUID node : nodes.keySet()) {
            if (node != null && (uuid2 == null || uuid2.equals(st.getParent(node))))
              uids.add(node);
          }
      }
    }
    return uids;
  }

  public Map<String, AttributeDefinition> getCachedOrBaseAttributes(UUID uid) {
    AttributeStorage as = AttributeStorage.getCachedStorage(this.srcTree, uid);
    if (as != null) return as.getActiveAttributeDefs();
    return getBaseAttributes(uid);
  }

  public UUID getCloneParent(UUID uid) {
    if (uid == null) return null;
    // return oldti.getCloneParent(uid);
    if (canBaseLoad(uid)) {
      Map<String, AttributeDefinition> attrs = getCachedOrBaseAttributes(uid);
      if (attrs != null) {
        AttributeDefinition at = attrs.get(TreeNode.ATTR_CLONE_PARENT);
        if (at != null && at.getRawValue() != null && at.getRawValue().getValue() != null)
          return UUID.fromString(at.getRawValue().getValue().toString());
      }
    }
    return null;
  }

  @Override
  public Collection<UUID> getChildren(UUID uuid) {
    return getBaseStorage().getChildren(uuid);
  }

  @Override
  public File getJSONResource(UUID uid, LockType lock) throws LockIsBusy {
    File is;
    is = getBaseStorage().getJSONResource(uid, lock);
    if (is != null) return is;
    if (hasCloneParent(uid)) return getJSONResource(getCloneParent(uid), lock);
    return null;
  }
  @Override
  public File getResource(UUID uuid, String resName) {
    File is;
    is = getBaseStorage().getResource(uuid, resName);
    if (is != null) return is;
    if (hasCloneParent(uuid)) return getResource(getCloneParent(uuid), resName);
    return null;
  }

  @Override
  public URI getLocation() {
    return getBaseStorage().getLocation();
  }

  @Override
  public Lock writeLock(UUID uid) {
    return getBaseStorage().writeLock(uid);
  }

  @Override
  public void writeUnlock(UUID uid) {
    getBaseStorage().writeUnlock(uid);
  }

  @Override
  public CacheStorage getCPCacheStorage(TreeDB dbfrom) {
    if(getBaseStorage() instanceof ICacheStorageProvider)
      return ((ICacheStorageProvider)getBaseStorage()).getCPCacheStorage(dbfrom);
    return null;
  }


  @Override
  public Collection<IMark> getStorageNodeProblems(UUID uid){
    return getBaseStorage().getStorageNodeProblems(uid);//TODO mode problems?
  }

}
