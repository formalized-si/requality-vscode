package com.unitesk.requality.core.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.api.IStoppable;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
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
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
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
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class LinkResolver extends CacheManipulator<TreeDB> implements IStoppable{
  private static String NAME="Link resolver";
  public static String REV_INITED = "_revInited";
  ReentrantReadWriteLock rrwl = new ReentrantReadWriteLock();

  private HashMap<String, Set<UUID>> namecache = new HashMap<>();
  ICacheStorageProvider cp = null;
  private LinksCacheMan lcm = null;
  private NamesCacheMan ancm = null;
  private boolean stopped = false;
 // private static Map<RequalityElement, Document> proxy = new HashMap<>();
  public static final int RESOLVE_MODE = Path.QID | Path.UV | Path.UUID | Path.XPATH;
  private TreeNode refTarget;
  private int refTargetState = 0;
  
  public TreeNode getRefTarget() {
    if(refTargetState!=0)
      return refTarget;
    return null;
  }

  @Override
  public CacheManipulatorInitializationStage getCacheState(SettingsManipulator sman, TreeDB treeDB, CacheInitializationStage stage) {
    if(stage == CacheInitializationStage.FULL || stage == CacheInitializationStage.INIT)
      return super.getCacheState(sman, treeDB, stage);
    return CacheManipulatorInitializationStage.NON_APPLIABLE;
  }
  public void loadRefTarget() {
    String s = (String) cp.getCacheStorage(null).getPSCM().getProjectSetting("_refTarget");
    if(s!=null) {
      TreeDB db = getRelatedTreeDB();
      if(db!=null) {
        refTarget = db.getNode(s);
      }
    }
    s = (String) cp.getCacheStorage(null).getPSCM().getProjectSetting("_refState");
    if(s!=null)
      refTargetState = Integer.parseInt(s);
  }
  public int getRefTargetState(TreeNode node) {
    if (refTarget != null && refTarget.equals(node)) return refTargetState;
    return 0;
  }

  public void setRefTarget(TreeNode node) {
    if (refTarget == null) refTargetState = node != null ? 1 : 0;
    else if (!refTarget.equals(node)) {
      refTargetState = 0;
      refTarget = null;
      cp.getCacheStorage(null).getPSCM().dropProjectSetting("_refTarget");
      cp.getCacheStorage(null).getPSCM().dropProjectSetting("_refState");
      return;
    }
    if (refTarget != null && refTarget.equals(node)) refTargetState = (this.refTargetState + 1) % 3;
    refTarget = node;
    if(node!=null)
      cp.getCacheStorage(null).getPSCM().setProjectSetting("_refTarget", node.getUUId());
    else
      cp.getCacheStorage(null).getPSCM().dropProjectSetting("_refTarget");
    cp.getCacheStorage(null).getPSCM().setProjectSetting("_refState", this.refTargetState);
  }
  public int getGlobalRefTargetState() {
    return refTargetState;
  }
  
  Pair<TreeRelationsCounter, TreeRelationsCounter> getRelatedProjects(TreeDB db){
    return getLCM().getRelatedProject(getLCM().getStorage(db), db.getLocalName(), db.getProjectName());
  }
  public TreeDB getRelatedTreeDB(){
    if(cp instanceof TreeDB)
      return (TreeDB) cp;
    return null;
  }
  
  public LinkResolver(TreeDB db) {
    this.cp = db;
    if(db == null)
      throw new RuntimeException("Link resolver cannot be inited on null TreeDB");
    if(!db.getListeners().contains(changeListener))
      db.addListener(changeListener);
  }

  public LinksCacheMan getLCM(){
    if(lcm==null)
      lcm = new LinksCacheMan(cp,false);
    return lcm;
  }
  public NamesCacheMan getNCM(){
    if(ancm == null)
      ancm = new NamesCacheMan(cp,false);
    return ancm;
  }
    
  public void close() {
    TreeDB db = (TreeDB) cp;
    db.removeListener(changeListener);
  }

  public NodeDesc[] findNodesByUFName(NodeDesc db, String path, Map<UUID, NodeDesc> map) {
    return getNodesByUFName(db, path, null, map, null);
  }

  /**
   * Method to get all possible elements with given User-Visible name Method to get Array of UUId's
   * of elements with given User Visible Id
   *
   * @param path array of uuids
   */
  @Deprecated
  private TreeNode[] getNodesByUFName(TreeDB db, String path) {
    return getNodesByUFName(db, path, null);
  }
  /**
   * Method to get Array of UUId's of elements with given User Visible Id
   *
   * @param path array of uuids
   */
  @Deprecated
  public TreeNode[] getNodesByUFName(TreeDB db, String path, List<String> types) {
    int pos = path.indexOf("::");
    TreeDB bdb = db;
    if (pos > -1) {
      String name = path.substring(0, pos);
      db = db.getTracker().getTreeDB(db,null, name, false, db.getMonitor());//TODO IDE monitor
      if (db != null) path = path.substring(pos + 2);
    }
    if (db != null) return db.getLinkResolver().getNodesByUFName(db, path, types);
    Set<TreeNode> curnodes = new HashSet<>();
    StringTokenizer st = new StringTokenizer(path, "/");
    if (!st.hasMoreTokens()) return new TreeNode[0];
    String nodeId = st.nextToken();
    //if we have node with name nodeId in requirement then use it
    TreeNode rnode =
        bdb.getRootNode()
            .findSingleWorkChildPlusName("Requirements")
            .findSingleWorkChildPlusName(nodeId);
    if (rnode != null) curnodes.add(rnode);

    rnode = bdb.getRootNode().findSingleWorkChildPlusName(nodeId);
    if (rnode != null) curnodes.add(rnode);
    //if cache of Requiremens names contains nodeId
    //then try to get appropriate node
    curnodes.addAll(findNodesById(bdb, nodeId));
    return findNodesByTokens(curnodes, st);
  }

  private static final Set<TreeNode> findNodesById(TreeDB db, String nodeId) throws NodeNotFoundException {
    Set<TreeNode> ret = new HashSet<>();
    if (db.getLinkResolver().containsName(nodeId)) {
      Set<UUID> nodes = db.getLinkResolver().getByName(nodeId);
      if (nodes.size() < 1) return ret;
      for (UUID uid : new HashSet<>(nodes)) {
        try{
          TreeNode nde = db.getNodeExc(uid);
          if (!ret.contains(nde) && nde != null) ret.add(nde);
        }
        catch(NodeNotFoundException nfe){
        }
      }
    }
    return ret;
  }

  private TreeNode[] findNodesByTokens(Set<TreeNode> curnodes, StringTokenizer st) {
    if (curnodes.size() == 0) {
      return new TreeNode[0];
    }

    String[] tokens = new String[st.countTokens()];
    int i = 0;
    while (st.hasMoreTokens()) tokens[i++] = st.nextToken();

    Iterator<TreeNode> it = curnodes.iterator();
    LinkedList<TreeNode> ll = new LinkedList<>();
    while (it.hasNext()) {
      TreeNode node = it.next();
      List<TreeNode> uids = findNodeByIdNamePath(node, tokens);
      if (uids != null) ll.addAll(uids);
    }
    return ll.toArray(new TreeNode[0]);
  }
  public List<TreeNode> findChildByIdName(TreeNode nde, String s) {
    List<TreeNode> ret = new ArrayList<>();
    for (TreeNode node : nde.getShownChildren())
      //coolapsed vnodes tends to be hidden in shownchildren
      if (node.getId().equals(s)
          || s.equals(node.getName())
          || node.getRawId().equals(s)
          || node.getSUUId().equals(s)) ret.add(node);
    return ret;
  }

  public List<TreeNode> findNodeByIdNamePath(TreeNode node, String[] tokens) {
    List<TreeNode> cur = new ArrayList<>();
    cur.add(node);
    for (String s : tokens) {
      List<TreeNode> newgen = new ArrayList<>();
      for (TreeNode node2 : cur) newgen.addAll(findChildByIdName(node2, s));
      cur = newgen;
      if (cur.isEmpty()) return cur;
    }
    return cur;
  }

  private String getName(NodeDesc nd) {
    if (nd != null) {
      AttributeDefinition al = nd.getAttributes().get(TreeNode.ATTR_NAME);
      if (al != null) {
        Object val = al.getRawValue().getValue();
        return val == null ? null : val.toString();
      }
    }
    return null;
  }

  private LinkedList<NodeDesc> formCurChilds(
      List<UUID> childs, String nodeId, Map<UUID, NodeDesc> nds) {
    LinkedList<NodeDesc> curnodes = new LinkedList<>();
    if (childs != null)
      for (UUID chi : childs) {
        NodeDesc rnode = null;
        NodeDesc nd = nds.get(chi);
        if (nd.getId().equals(nodeId)) rnode = nd;
        String name = getName(nd);
        if (name != null && name.equals(nodeId)) rnode = nd;
        if (rnode != null) curnodes.add(rnode);
        if (VirtualNode.TYPE_NAME.equals(nd.getType())) {
          if (nd.getAttributes().containsKey(VirtualNode.ATTR_COLLAPSEVNODE)){
            AttributeDefinition ad = nd.getAttributes().get(VirtualNode.ATTR_COLLAPSEVNODE);
            if (ad!=null && ad.getRawValue()!=null && (boolean) ad.getRawValue().getValue()
                == true) {
              curnodes.addAll(
                  formCurChilds(nd.getChildrenUUId(), nodeId, nds)); //look in hidden vnode
            }
          //TODO check
          }
        }
      }
    return curnodes;
  }

  public NodeDesc[] getNodesByUFName(
      NodeDesc root,
      String path,
      List<String> types,
      Map<UUID, NodeDesc> nds,
      Map<UUID, LinkedList<UUID>> childfunc) {
    LinkedList<NodeDesc> curnodes = new LinkedList<>();
    TreeDB db = null;
    int pos = path.indexOf("::");
    if (pos > -1) {
      String name = path.substring(0, pos);
      //TODO rt == null?
      //TODO revisioning mech is not working here
      TreeDB rel = getRelatedTreeDB();
      if(rel!=null)
        db = rel.getTracker().getTreeDB(db,null, name, false, rel.getMonitor());//TODO IDE monitor
      if (db != null) path = path.substring(pos + 2);
      else throw new RuntimeException("Project '" + name + "' was not found or can not be nodeLoaded");
    }
    if (db != null && db != this.getRelatedTreeDB()) return db.getLinkResolver().getNodesByUFName(root, path, types, nds, childfunc);
    StringTokenizer st = new StringTokenizer(path, "/");
    if (!st.hasMoreTokens()) return new NodeDesc[] {root}; //TODO test
    String nodeId = st.nextToken();
    //if we have node with name nodeId in requirement then use it

    List<UUID> childs = null;
    if (childfunc != null) childs = childfunc.get(root.getUUId());
    else childs = root.getChildrenUUId();
    curnodes = formCurChilds(childs, nodeId, nds);
    //	        if(rnode != null)
    //	        	curnodes.add(rnode);
    //if cache of Requiremens names contains nodeId
    //then try to get appropriate node
    if (containsName(nodeId)) {
      Set<UUID> nodes = getByName(nodeId);
      if (nodes.size() < 1) return null;
      for (UUID uid : nodes) {
        NodeDesc nde = nds.get(uid);
        if (!curnodes.contains(nde)) curnodes.add(nde);
      }
    }
    if (curnodes.size() == 0) {
      return new NodeDesc[0];
    }

    String[] tokens = new String[st.countTokens()];
    int i = 0;
    while (st.hasMoreTokens()) tokens[i++] = st.nextToken();

    Iterator<NodeDesc> it = curnodes.iterator();
    LinkedList<NodeDesc> ll = new LinkedList<>();
    while (it.hasNext()) {
      NodeDesc node = it.next();
      NodeDesc uid = findNodeDesc(node, tokens, nds, childfunc);
      if (uid != null) ll.add(uid);
    }
    return ll.toArray(new NodeDesc[0]);
  }
  
  public NodeDesc findNodeDesc(
      NodeDesc node,
      String[] tokens,
      Map<UUID, NodeDesc> nds,
      Map<UUID, LinkedList<UUID>> childfunc) {
    NodeDesc temp = node;
    for (int i = 0; i < tokens.length && temp != null; i++) {
      if(tokens[i].isEmpty())
        continue;
      Collection<UUID> chi = null;
      if (childfunc != null) chi = childfunc.get(temp.getUUId());
      else chi = temp.getChildrenUUId();
      if (chi == null) return null;
      temp = null;
      for (UUID uid : chi) {
        NodeDesc nd = nds.get(uid);
        if (nd.getId().equals(tokens[i])) {
          temp = nd;
          continue;
        }
        String name = getName(nd);
        if (name != null && name.equals(tokens[i])) {
          temp = nd;
          continue;
        }
        if (node.getSUUId().equals(tokens[i])) {
          temp = nd;
          continue;
        }
      }
      if (temp == null) return null;
    }
    return temp;
  }
  
  
  public List<TreeNode> findNodesByPathIgnoreDisposed(TreeNode node, String akey, TreeDB db, String qId, int mode) throws NodeNotFoundException {
    return findNodesByPath(node, akey, db, qId, mode, true);
  }

  public List<TreeNode> findNodesByPathWithDisposed(TreeNode node, String akey, TreeDB db, String qId, int mode) throws NodeNotFoundException {
    return findNodesByPath(node, akey, db, qId, mode, false);
  }
  public List<TreeNode> findNodesByPath(TreeNode node, String akey, TreeDB db, String qId, int mode,boolean denydisposed) throws NodeNotFoundException {
    //TODO multimethod
    if (qId == null) return null;
    if (node != null){
      db = node.getTreeDB();
      if(node.isVRoot())
        db = node.getVirtualDB();
    }
    int pos = qId.indexOf("::");
    if (pos > -1) {
      String name = qId.substring(0, pos);
      TreeDB adb = null;
      //TODO db == null
      adb = db.getTracker().getTreeDB(db, null, name, false, db.getMonitor());//TODO test
      qId = qId.substring(pos + 2);//fix exc on further uuid.fromstring
      //TODO look into loading case
      TreeStateManager tsm = db.getTracker().getTreeStateManager(); 
      if (adb != null && !adb.isDisposed() && (db == adb || adb.getCMM().getLinkManipulator().getNamesInited(adb))) {
        //adb.addListener(changeListener);
        db = adb;
      } else 
        return Collections.emptyList();//db is not nodeLoaded
    }
    Set<TreeNode> ret = new HashSet<>();
    TreeNode root = db.getRootNode();
    if ((mode & Path.UUID) == Path.UUID && qId.length() == 36) {
      try {
        UUID uid = UUID.fromString(qId);
        if(uid!=null){
          TreeNode nde = db.getNodeExc(uid);
          if (nde != null && (!denydisposed||!nde.isDisposed())){
            LinkedList<TreeNode >ll = new LinkedList<>();
            ll.add(nde);
            return ll;
          }
          return Collections.emptyList();
        }
      } catch (IllegalArgumentException ie) {
        //incorrect uid
      }
    }
    if (((mode & Path.QID) == Path.QID || (mode & Path.UV) == Path.UV)
        && (qId.equals("/") || qId.isEmpty())) {
      return Arrays.asList(new TreeNode[] {root});
    }
    TreeNode[] tnodes = null;
//    try{
//        if ((mode & Path.XPATH) == Path.XPATH) tnodes = findNodesByXPath(db.getRootNode(), qId);
//    }
//    catch(Exception e){
//        //incorrect xpath, //TODO message
//    }
    if (tnodes != null && tnodes.length > 0)
      for(TreeNode nde:tnodes)
        if(!denydisposed || !nde.isDisposed())
          ret.add(nde);
        //ret.addAll(Arrays.asList(tnodes));
    qId = qId.replace((char) 160, ' ');
    if (((mode & Path.UV) == Path.UV || (mode & Path.QID) == Path.QID)) {
      StringTokenizer st = new StringTokenizer(qId, "/");
      String step = st.nextToken();
      Set<TreeNode> initSet = new HashSet<>();
      TreeNode rt = db.getRootNode();
      if (rt != null) {
        initSet.add(db.getRootNode());
        rt =
            db.getRootNode().findSingleWorkChildPlusName(Requirement.getTypeRootQId().substring(1));
        if (rt != null && (!denydisposed||!rt.isDisposed())) initSet.add(rt);
      }
      initSet = findChildByIdName(db, initSet, step, (mode & Path.CREATE) == Path.CREATE);
      if ((mode & Path.UV) != 0) initSet.addAll(findNodesById(db, step));
      while (st.hasMoreTokens()) {
        step = st.nextToken();
        initSet = findChildByIdName(db, initSet, step, (mode & Path.CREATE) == Path.CREATE);
      }
      for(TreeNode nde:initSet)
        if(!denydisposed||!nde.isDisposed())
          ret.add(nde);
    }
    if ((mode & Path.TAG) == Path.TAG) {
      //TODO look for tag
    }
    return new LinkedList<>(ret);
  }

  private Set<TreeNode> findChildByIdName(TreeDB db, Set<TreeNode> initSet, String step, boolean create) {
    Set<TreeNode> next = new HashSet<>();
    for (TreeNode cur : initSet) {
      if (cur != null) {
        List<TreeNode> next2 = cur.findWorkChildPlusName(step);
        if (next2 != null && next2.size() > 0) next.addAll(next2);
        next2 = cur.findShownChildsPlusName(step);
        if (next2 != null && next2.size() > 0) next.addAll(next2);
      }
    }
    if(create && next.isEmpty() && (initSet.size() == 1 || hasRoot(initSet)&&initSet.size() == 2)){
      for (TreeNode cur : initSet) {
        if(cur.isRoot())
          continue;
        next.add(cur.createChild(step));
      }
    }
    return next;
  }

  private boolean hasRoot(Set<TreeNode> initSet) {
    for(TreeNode node:initSet)
      if(node.isRoot())
        return true;
    return false;
  }


//  private TreeNode[] findNodesByXPath(TreeNode targ, String path) {
//    TreeNode root = targ.getTreeDB().getRootNode();
//    RequalityElement rt = RequalityElement.toElement(root.getRoot().getUUId(), root);
//    synchronized (proxy) {
//      if (!proxy.containsKey(rt)) {
//        if (!proxy.containsValue(rt.getDocument()))
//          proxy.put(rt, DocumentHelper.createDocument(rt));
//        else proxy.put(rt, rt.getDocument());
//      }
//    }
//    RequalityElement.toElement(targ.getRoot().getUUId(), targ.getTreeDB().getRootNode())
//        .setDocument(proxy.get(rt));
//    RequalityElement rer = RequalityElement.toElement(targ.getRoot().getUUId(), targ);
//    org.dom4j.XPath xpath;
//    try {
//      xpath = proxy.get(rt).createXPath(path);
//    } catch (InvalidXPathException icpe) {
//      return null;
//    }
//    Object result = xpath.evaluate(rer);
//    if (result instanceof Collection<?>) {
//      List<TreeNode> res = new ArrayList<>();
//      @SuppressWarnings("unchecked")
//      Iterator<Element> it = ((Collection<Element>) result).iterator();
//      while (it.hasNext()) {
//        Element re = it.next();
//        res.add(((RequalityElement) re).getWrappedTreeNode());
//      }
//      if (res.size() == 0) return null;
//
//      return res.toArray(new TreeNode[0]);
//    }
//    if (result instanceof RequalityElement) {
//      return new TreeNode[] {((RequalityElement) result).getWrappedTreeNode()};
//    }
//    return null;
//  }
  
  public static String parseTreeDBName(String path) {
    int n = path.indexOf("::");
    if(n>-1)
      return path.substring(0,n);
    return null;
  }


  public boolean testNodeByPath(TreeNode src, TreeNode trg, String path) {
    //TreeNode targ = db.getNode(targuid);
    if (path == null) return false;
    String dbname = parseTreeDBName(path);
    TreeDB db = src.getTreeDB();
    if(dbname!=null)
      db = db.getTracker().getTreeDB(db,null, dbname, true, db.getMonitor());//TODO IDE monitor
    try {
      TreeNode ntarg = db.getNode(path, Path.QID | Path.UV | Path.UUID); //TODO crossproject
      if (ntarg != null) return ntarg.getUUId().equals(trg.getUUId());
    } catch (Exception e) {
    }
    TreeNode root = src.getTreeDB().getRootNode();
//    RequalityElement re = RequalityElement.toElement(root.getRoot().getUUId(), root);
//    synchronized (proxy) {
//      if (!proxy.containsKey(re)) {
//        if (re.getDocument() != null) proxy.put(re, re.getDocument());
//        else proxy.put(re, DocumentHelper.createDocument(re));
//      }
//    }
//    RequalityElement.toElement(root.getRoot().getUUId(), db.getRootNode())
//        .setDocument(proxy.get(re));
//    //RequalityElement.toElement(root.getRoot().getUUId(),targ);
//    org.dom4j.XPath xpath;
//    try {
//      xpath = proxy.get(re).createXPath(path);
//    } catch (InvalidXPathException icpe) {
//      return false;
//    }
//    //TODO crossproject
//    if (trg == null
//        || trg.getRoot() == null
//        || RequalityElement.toElement(trg.getRoot().getUUId(), trg) == null) return false;
//    return xpath.matches(RequalityElement.toElement(trg.getRoot().getUUId(), trg));
    return false;
  }

  public Set<UUID> getNameCache(String nid) {
    return getByName(nid);
  }

  public ReferenceValue[] getRefValuesByAttributeName(TreeNode node, String key) {
    if (node == null) return new ReferenceValue[]{new ReferenceValue(key, "Source not found!")};
    return getRefValuesByAttributeName(node, key, null);
    //		TreeNode node = attrs().getLinkAttributeValue(key);
    //		return node==null?"Missing reference: '"+attrs().getStringAttributeValue(key)+"'":node.getUserVisibleName(false);
  }

  public static ReferenceValue[] getRefValuesByAttributeName(TreeNode targ, String key, String label) {
//    List<TreeNode> nde =
//        targ.getTreeDB()
//            .getLinkResolver()
//            .getStorage()
//            .resolveLink(targ.getTreeDB().getLinkResolver(), targ, key, null, label, true, true);
    Attribute a = targ.attrs().getAttribute(key);
    if (a!=null){
      Object val = a.getValue();
      if(val!=null)
      {
        if(a.getType()==AttributeType.REFERENCE)
        {
          ReferenceValue v = (ReferenceValue) val;
          if(v.hasErrorsOnResolve(targ.attrs()))
            return new ReferenceValue[]{v};
          v.getNodes(targ.attrs());
          return new ReferenceValue[]{v};
//            ret.add(node);
//          return ret.toArray(new Object[0]);
        }else
        {
          List<ReferenceValue> ret = new LinkedList<>();
          ListValue lv = (ListValue) a.getValue();
          for(Object o : lv.getValues()){
            if(o == null)
              ret.add(new ReferenceValue(key, ReferenceValueProvider.NULL_MESSAGE.substring(1)));
            else{
              ReferenceValue v = (ReferenceValue) o;
              if(v.hasErrorsOnResolve(targ.attrs()))
                ret.add(v);
              else
                ret.add(v);
            }
          }
          return ret.toArray(new ReferenceValue[0]);
        }
      }
      else
        return new ReferenceValue[]{new ReferenceValue(key, ReferenceValueProvider.NULL_MESSAGE)};
    }
//    if (label == null) label = targ.attrs().getStringAttributeValue(key);
//    return (nde == null || nde.size() == 0)
//        ? ("Missing reference: '" + label + "'")
//        : nde.iterator().next().getUserVisibleName(false);
    return new ReferenceValue[]{new ReferenceValue(key, ReferenceValueProvider.NULL_MESSAGE)};
  }

  public Map<TreeNode, String> getLinksToNode(TreeNode rnode, String key) {
    return getLinksToNode(rnode, key, false);
  }

  private void removeIncorrectLinks(TreeNode node) {
    List<RelationRecord> rev = getDirectLinksCacheItems(node);
    //rl.unlock();
    List<RelationRecord> todel = null;
    if (rev != null && rev.size() > 0) {
      for (RelationRecord rr: rev){
        CheckResult cr = checkLinkIsCorrect(node.getTreeDB(), rr);
        if (cr != CheckResult.OK) {
          if(todel == null)
            todel = new LinkedList<>();
          todel.add(rr); 
        }
      }
    }
    if(todel!=null){
      getLCM().updateLinks(node.getTreeDB(), todel, Collections.emptyList());
    }
  }

  
  private CheckResult checkLinkIsCorrect(TreeDB db, RelationRecord rr) {
    TreeDB dbfrom = db,dbto = db;
    if(rr.isCP()){
      if(db.getProjectName().equals(rr.projectFrom))
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
      UUID[] uids = rv.getNodeUUIDs(from.attrs());
      if(uids != null)
        for(UUID node:uids)
          if(node.equals(to.getUUId())||to.getCloneParentUUId()!=null && to.getCloneParentUUId().equals(node))
            return CheckResult.OK;//TODO think
    }else{
      ListValue lv = a.getGValue();
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
  public void handleLinksAttributesChanges(
      TreeNode node, Set<String> ofint,Map<String,Attribute>newa, byte flag){
    if (getLCM().getStorage(node.getTreeDB()) == null) return;
    List<RelationRecord> toa = null,tod = null,cptoa = null,cptod = null;
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
      if (a != null && a.isReference() && !a.isCP()) {
        Map<TreeNode, String>list = ReferenceValue.getValuesMap(node.attrs(), a.getType(),a.getValue());
        if(list!=null)
          for(TreeNode nde:list.keySet()){
            boolean cpr = isCPRelation(node, nde);
            if(!cpr && toa == null)
              toa = new LinkedList<>();
            if(cpr && cptoa == null)
              cptoa = new LinkedList<>();
            addValue(toa, cptoa, node, nde, key, cpr, list.get(nde));
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
        }
      }
    }
    if(toa!=null || tod!=null){
      CacheStorage cs=getLCM().getStorage(node.getTreeDB());
      cs.writeLock(LinksCacheMan.REVERT_LINKS_SECTION_NAME);
      try{
        lcm.updateLinks(node.getTreeDB(), tod!=null?tod:Collections.emptyList(), toa!=null?toa:Collections.emptyList());
      }
      finally{
        cs.writeUnlock(LinksCacheMan.REVERT_LINKS_SECTION_NAME);
      }
    }
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
  public boolean handleLinksAttributesChanges(
      TreeNode node, ChangesSet<Attribute> params, byte flag) {
    if (getLCM().getStorage(node.getTreeDB()) == null) return false;
    Set<String> ofint = null;
    //Map<String, Attribute> oldA = params.getOldMap();
    for (String s:params.getAll()) {
      Attribute olda = params.getOld(s);
      Attribute a = params.getNew(s);
      if (a!=null && a.isReference()
          || a == null && olda != null && olda.isReference()){
        if(ofint == null)
          ofint = new HashSet<>();
        ofint.add(s); 
      }
    }
    if(ofint!=null) {
      handleLinksAttributesChanges( node,ofint,params.getNewMap(), flag);
      return true;
    }
    return false;
  }

  private static final void addValue(
      List<RelationRecord> list, List<RelationRecord> cplist, RelationRecord rr) {
    if(!rr.isCP())
      list.add(rr);
  }
  private static final void addValue(
      List<RelationRecord> toa, List<RelationRecord> cptoa, TreeNode nodeFrom, TreeNode nodeTo, String key, boolean cp, String vers) {
    if(!cp)
	  toa.add(new RelationRecord(nodeFrom.getTreeDB().getProjectName(), nodeFrom.getUUId(),nodeTo.getUUId(), key, vers));
  }


  private ITreeChangeListener changeListener =
      new DefaultTreeChangeListener() {

        @Override
        public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {
         if(m == Moment.BEFORE)
            return;
          TreeDB dbfrom = node.getTreeDB();
          if (getLCM().getStorage(dbfrom) == null || dbfrom!=getRelatedTreeDB()) return;
          recCheck(node);
        }

        @Override
        public void deleted(TreeNode nodeDesc, byte flag, Moment m) {
          TreeDB dbfrom = nodeDesc.getTreeDB();
          LinksCacheMan lcm = getLCM();
          if (lcm.getStorage(dbfrom) == null || m == Moment.AFTER|| dbfrom!=getRelatedTreeDB()) return;
          Set<TreeNode> nds = new HashSet<>();
          TreeDB activeDB = getRelatedTreeDB();
          if(activeDB == null)
            activeDB = dbfrom;
          CacheStorage cs = lcm.getStorage(activeDB);
          cs.writeLock(LinksCacheMan.REVERT_LINKS_SECTION_NAME);
          if(lcm.cachedRelations!=null)
            lcm.cachedRelations.remove(nodeDesc.getUUId());
          try{
            recAddTreeNodes(nodeDesc, nds);
            CacheTransactionDescription t = null;
            try{
              t = cs.startCacheTransaction("Delete node "+nodeDesc.getUUId(), CacheStorage.COMMIT_ALL);
              for (TreeNode node : nds) {
                removeIncorrectRevLinks( node);
                removeIncorrectLinks( node);
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
            cs.writeUnlock(LinksCacheMan.REVERT_LINKS_SECTION_NAME);
          }

          //LinksStorage.removeRevKey(getStorage(),nodeDesc.getSUUId());
        }

        @Override
        public void created(TreeNode node, byte flag) {
          TreeDB dbfrom = node.getTreeDB();
          if (getLCM().getStorage(getRelatedTreeDB()) == null || dbfrom!=getRelatedTreeDB()) return;
          CacheTransactionDescription t = null;
          CacheStorage cs = getLCM().getStorage(dbfrom);
          cs.writeLock(LinksCacheMan.REVERT_LINKS_SECTION_NAME);
          try{
            t = cs.startCacheTransaction("resolve created links",CacheStorage.COMMIT_SINGLE);
            Set<String> ofint = null;
            for (String s : node.attrs().getLocalAttributeKeys()) {
              Attribute a = node.attrs().getAttribute(s);
              if (a == null || a.isReference())
              {
                if(ofint == null)
                  ofint = new HashSet<>();
                ofint.add(s);
              }
            }
            if(ofint!=null && !ofint.isEmpty())
              handleLinksAttributesChanges( node, ofint, node.attrs().getLocalAttributes(), flag);
            //if(getStorage().getStoredRevKeys().contains(node.getSUUId())){
            removeIncorrectLinks( node);
            removeIncorrectRevLinks( node);
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
            cs.writeUnlock(LinksCacheMan.REVERT_LINKS_SECTION_NAME);
          }
        };

        

        private void removeIncorrectRevLinks(TreeNode node) {
          List<RelationRecord> rev = getDirectLinksCacheItems(node);
          //rl.unlock();
          List<RelationRecord> todel = null;
          if (rev != null && rev.size() > 0) {
            for (RelationRecord rr: rev){
              CheckResult cr = checkLinkIsCorrect(node.getTreeDB(), rr);
              if (cr != CheckResult.OK) {
                if(todel == null)
                  todel = new LinkedList<>();
                todel.add(rr); 
              }
            }
          }
          if(todel!=null){
             getLCM().updateLinks(node.getTreeDB(), todel, Collections.emptyList());
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
          if(getRelatedTreeDB()!=null&&node.getTreeDB()!=getRelatedTreeDB())
            return;
          if(LinkResolver.this.handleLinksAttributesChanges(node,params,flag))
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
                  if(node.getTreeDB()==getRelatedTreeDB())
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
                if(node.getTreeDB()==getRelatedTreeDB())
                  moved(
                      node,
                    (TreeNode) ce.getArguments()[0],
                    (TreeNode) ce.getArguments()[1],
                    Moment.AFTER, 
                    flag);
              }break;
              case CREATE:{
                if(ce.getMoment()==Moment.BEFORE)
                  continue;
                
                TreeNode node = ce.getTreeNodeTarget(); 
                if(node.getTreeDB()==getRelatedTreeDB())
                  created(node, flag);
              }break;
              case DELETE:{
                if(ce.getMoment()==Moment.BEFORE)
                  continue;
                
                TreeNode node = ce.getTreeNodeTarget(); 
                if(node.getTreeDB()==getRelatedTreeDB())
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
  private boolean initCaches(TreeDB db, IProgressMonitor mon) {
    stopped = false;
    Global.registerStoppable(this);
    TreeDB dba = getRelatedTreeDB();
    if(dba!=null)
      dba.removeListener(changeListener);
    dba = db;
    
    String name = dba.getLocalName();
    try{
      if(mon.isCanceled()) return false;
      if(stopped || !initNamesCache(dba, mon))
        return false;
      if(stopped || !initRevLinks(dba, mon))
        return false;
    }finally{
      dba.addListener(changeListener);
      Global.unRegisterStoppable(this);
    }
    if(mon.isCanceled()) return false;
    loadRefTarget();
    return true;
  }
  @Override
  public void clearNodeCache(TreeDB db, SettingsManipulator settingsStore, TreeNode node) {
    removeLocalCacheForNode(db, node.getUUId());
  }

  public void removeLocalCacheForNode(TreeDB db, UUID node) {
    CacheStorage cs = getLCM().getStorage(db);
    CacheTransactionDescription tr = cs.startCacheTransaction("clear cache for "+node+" (ls)", CacheStorage.COMMIT_ALL);
    try{
      getLCM().removeRecords(db, node);
      getNCM().deleteName(node);
    }catch(Exception r){
      if(tr != null && tr.error == null) {
        tr.error("already rolled back");
        cs.rollback(tr);
      }
      r.printStackTrace();
    }
    finally {
      if(tr != null && tr.error == null)
        cs.commit(tr);
    }
  }
  public void removeCPCacheForNode(TreeDB db, String lname, String pname, UUID node) {
    getLCM().removeCPRecords(db,lname,pname, node);
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


  public void initNames(TreeDB db, IProgressMonitor mon) {
    initNamesCache(db, mon);
  }

  private List<RelationRecord> getDirectLinksCacheItems(
      TreeNode node, Set<String> ofint) {
    List<RelationRecord> local = getLCM().getDirectLinks(node, ofint);
    return local;
  }
  private List<RelationRecord> getDirectLinksCacheItems(
      TreeNode node) {
    TreeDB dba = node.getTreeDB();
    List<RelationRecord> local = getLCM().getDirectLinks(node);
    return local;
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

  
  private boolean initNamesCache(TreeDB adb, IProgressMonitor mon) {
    TreeDB db = getRelatedTreeDB();
    if(db == null)
      db = adb;
    UUID ruid = db.getRootNodeUUID();
    if(mon.isCanceled())return false;
    if(ruid == null || stopped){
      RLogger.logError("Null root for db "+db.getVisibleName()+" in "+db.getTracker().toString());
      return false;
    }
    //UUID ruid = root.getUUId();
    TreeNode root = db.getRootNode();
    if(root == null)
      return false;
    //String val = (String) db.getPSCacheMan().getProjectSetting("_namesInited");
    //if/ (val == null || !val.equals("true")) {
      if(mon.isCanceled())return false;
      DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(db.getRootNode(), false, false, true, false, false);
      Iterator<TreeNode> it = dftw.iterator();
//      saveRevLinks(null, true);
      long l = System.currentTimeMillis();
      CacheTransactionDescription vala = null;
      namecache = null;
      if(stopped)
        return false;
      WriteLock wl = rrwl.writeLock();
      wl.lock();
      try {
        namecache = new HashMap<>();
      } finally {
        wl.unlock();
      }
      CacheStorage cs = getNCM().getStorage();
      try{
        vala = cs.startCacheTransaction("init names",CacheStorage.COMMIT_ALL);
        int cnt = 0;
        while (it.hasNext()) {
          if(stopped || mon.isCanceled()) {
            return false;//TODO check nameChache state
          }
          cnt++;
          TreeNode node = it.next();
          String name = node.getName();
          if(name!=null){
            if(!namecache.containsKey(name))
              namecache.put(name, new HashSet<>());
            namecache.get(name).add(node.getUUId());
            getNCM().setName(node.getUUId(), name);
            //init is called during loading
    //        root.getTreeDB().getCacheStorage().setCacheValue(db.getRootNode().getUUId(), "names","name", node.getSUUId(), name);
          }
        }
        RLogger.logDebug("handled "+cnt+" nodes with links");
        db.getPSCacheMan().setProjectSetting("_namesInited", "true");
        if(vala != null && vala.error == null) {
          vala.error("already commited");
          cs.commit(vala);
        }
      }
      catch(Exception e){
        if(vala != null && vala.error == null)
          cs.rollback(vala);
        throw e;
      }
      if(CoreUtils.DEBUG_LEVEL>0)
        RLogger.logDebug("rslv names " + (System.currentTimeMillis()-l) +" "+db.getVisibleName()+ db.getTracker().getTreeStateManager().getRevisionOrHeadString(db));
      return true;
//      saveRevLinks(null, true);
    //}else
    //{
      //namecache = new HashMap<>();
    //}
  }
  public boolean getNamesInited(TreeDB db) {
    Object sett = db.getPSCacheMan().getProjectSetting("_namesInited");
    return sett!=null && sett.equals("true");
  }
//  private void setRevInited(String root, String val){
//    TreeDB db = getTreeDB();
//    String s = (String) db.getPSCacheMan().getProjectSetting(PROJECT_SETTS_REV_CACHE_INITED);
//    if(s==null || !val.equals(s))
//      db.getPSCacheMan().setProjectSetting(PROJECT_SETTS_REV_CACHE_INITED, val);
//  }
  private boolean initRevLinks(TreeDB dba, IProgressMonitor mon) {
    TreeDB db = getRelatedTreeDB();
    if(db == null)
      db = dba;
    TreeNode root = null;
    if(db!=null && !mon.isCanceled())
      root = db.getRootNode();
    else return false;
    if (root == null) return false;
    UUID rootuuid = root.getUUId();
   
    String val = (String) db.getPSCacheMan().getProjectSetting(REV_INITED);
    if (val == null || !val.equals("true")) {
      DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(root, false, false, true, false, false);
      Iterator<TreeNode> it = dftw.iterator();
//      saveRevLinks(null, true);
      long l = System.currentTimeMillis();
      CacheTransactionDescription vala = null;
      CacheStorage cs = root.getTreeDB().getCacheStorage();
      if(stopped || mon.isCanceled())
        return false;
      try{
        vala = cs.startCacheTransaction("init links",CacheStorage.COMMIT_ALL);
        Set<String> ofint = Collections.emptySet();
        while (it.hasNext()) {
          if(stopped || mon.isCanceled())
            return false;
          TreeNode node = it.next();
          for (String key : new HashSet<>(node.attrs().getLocalAttributeKeys())) {
            Attribute a = node.attrs().getAttribute(key);
            if (a != null)
              if (a.isReference()) {
                if(ofint.isEmpty())
                  ofint = new HashSet<>();
                ofint.add(key);
              }
          }
          if(!ofint.isEmpty())
            node.getTreeDB().getLinkResolver().handleLinksAttributesChanges(
                node, ofint, node.attrs().getLocalAttributes(), OFlag.NONE);
          if(!ofint.isEmpty())
            ofint.clear();
        }
      }
      catch(Exception e){
        if(vala != null && vala.error == null) {
          vala.error("already rolled back");
          cs.rollback(vala);
        }
        RLogger.logError(e);
        throw e;
      } finally {
        //setRevInited(rootuuid.toString(), "true");
        if(vala != null && vala.error == null) {
          vala.error("already commited");
          cs.commit(vala);
        }
      }
      if(CoreUtils.DEBUG_LEVEL>0) {
    	TreeStateManager tsm = db.getTracker().getTreeStateManager();
        RLogger.logDebug("rslv " + (System.currentTimeMillis()-l) +" "+root.getTreeDB().getVisibleName()+ ((tsm.getRevisionInfo(db)==null)?" working":(" "+tsm.getRevisionOrHeadString(db)))); 
      }
//      saveRevLinks(null, true);
    }
    return true;
  }
  public  Map<UUID,String> getLinksToNode(TreeDB dbb, UUID uid, String key) {
    List<RelationRecord> st = null;
    TreeDB dba = dbb;
      st = getLCM().getRevLinks(dbb, uid, key);
    Map<UUID, String> revs = null;
    //TODO optimize for cp
    if(st!=null)
    for (RelationRecord rr : st) {
      TreeDB db = dba,adb = db;
      if(rr.projectFrom!=null&&rr.isCP()){
        adb = db.getTracker().getTreeDB(db,null, rr.projectFrom, false, db.getMonitor());//TODO IDE monitor
        //key = key;//it is not use :(
      }
      UUID nodeFrom = rr.nodeFrom;
      ITreeStorage alt = adb == null ? null : adb.getStorage();
      if(alt != null && alt.canLoad(nodeFrom)){
        TreeNode virtNode = db.getCachedNodeOrVirt(nodeFrom);
        Map<String, AttributeDefinition> defs = 
          virtNode!=null? virtNode.attrs().getActiveAttributeDefs():alt.getAttributes(nodeFrom);
        if(defs == null) {
          RLogger.logDebug("Attempt to load attribute defs for non-exist node "+nodeFrom);
          continue;
        }
        AttributeDefinition ad = defs.get(key);
        if (ad != null && ad.isReference()) {
          if(revs == null)
            revs = new HashMap<>();
          revs.put(nodeFrom, rr.nodeToVersion);
        }
      }
    }
    if(revs == null)
      return Collections.emptyMap();
    return revs;
  }
  
  public Map<String, Map<TreeNode, String>> getLinksToNode(TreeNode node, boolean validate) {
    List<RelationRecord> st = null;
    TreeDB dba = node.getTreeDB();
      st = getLCM().getRevLinks(dba, node.getUUId());
    Map<String, Map<TreeNode, String>> revs = null;
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
        String key = rr.key;
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
            Map<TreeNode, String> k = revs.get(key);
            if(k == null) {
              k = new HashMap<>();
              revs.put(key, k);
            }
            k.put(nodeFrom, rr.nodeToVersion);
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

  public Map<TreeNode,String> getLinksToNode(TreeNode node, String key, boolean validate) {
    List<RelationRecord> st = null;
    TreeDB dba = node.getTreeDB();
      st = getLCM().getRevLinks(dba, node.getUUId(), key);
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

  
  public Map<UUID, Map<String, Map<TreeNode, String>>> getAllLinksToNode(Set<TreeNode> nodes) {
    List<RelationRecord> st = null;
    TreeDB dba = getRelatedTreeDB();
      st = getLCM().getAllRevLinks(dba);
    Map<UUID,Map<String,Map<TreeNode, String>>> revs = null;
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
        if(revs == null)
          revs = new HashMap<>();
        if(!revs.containsKey(rr.nodeTo))
          revs.put(rr.nodeTo, new HashMap<>());
        Attribute a = nodeFrom.attrs().getAttribute(key);
        if (a != null && nodeFrom.attrs().isReference(key)) {
          boolean isvalid = true;
          String akey = key;
          boolean mult = false;
          int al = 0;
          if (isvalid) {
            
            if(!revs.get(rr.nodeTo).containsKey(key))
              revs.get(rr.nodeTo).put(key, new HashMap<>());
            revs.get(rr.nodeTo).get(key).put(nodeFrom, rr.nodeToVersion);
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
  private Map<String, Map<TreeNode, String>> getLinksFromNode(TreeNode node) {
    List<RelationRecord> st = null;
    TreeDB dba = node.getTreeDB();
    st = getLCM().getDirectLinks(node);
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
  private boolean containsName(String nodeId) {
    Set<UUID> bn = getByName(nodeId);
    return bn!=null && bn.size()>0;
  }
  /**
   * Method to update cached requirement name.
   *
   * @param node requirement to update name
   */
  public void updateNameInCache(UUID uid, String newName) {
    if(getRelatedTreeDB() == null)
      return;//cross-project
    //TreeDB db = db;
    Object old = getNCM().getName(uid);
    boolean eq = old!=null&&old.equals(newName) || old == null && newName == null;
    if(!eq){
      if(old!=null){
        Set<UUID> set = namecache.get(old.toString());
        if(set!=null)
          set.remove(uid);
        getNCM().deleteName(uid);
      }
      
      if(newName!=null){
        if(!namecache.containsKey(newName))
          namecache.put(newName, new HashSet<>());
        namecache.get(newName).add(uid);
        getNCM().setName(uid, newName);
      }
    }
    
  }

  private Set<UUID> getByName(String nodeId) {
    if(getRelatedTreeDB() == null)
      return Collections.emptySet();//cross-project
    if(namecache.containsKey(nodeId))
      return namecache.get(nodeId);
    //TreeDB db = db;
    UUID root = getRelatedTreeDB().getRootNodeUUID();
    if(root == null)
      return Collections.emptySet();
    //Map<String, List<Object>> store = db.getCacheStorage().getCacheMapByValue(db.getRootNode().getUUId(), "names", "names");
    Set<UUID> val = getNCM().getByName(nodeId); 
    if(val!=null && val.size()>0)
      namecache.put(nodeId, val);
    return namecache.get(nodeId);
  }

  public void initCache(TreeNode next, boolean b) {
    changeListener.created(next, OFlag.NONE);
  }
  
  @Override
  protected OperationResult innerInitFirst(TreeDB db, SettingsManipulator cman, IProgressMonitor mon) {
    return initCaches(db, mon)?OperationResult.SUCEEDED:OperationResult.UNSUCEEDED;
  }
  @Override
  protected OperationResult innerDropCache(TreeDB db, SettingsManipulator cman) {
    getLCM().removeLocalRevCache(db);
    getNCM().removeAll(db);
    return OperationResult.SUCEEDED;
  }

  @Override
  public boolean isCrossProjectCache() {
    return false;
  }

  @Override
  public OperationResult innerInitNotFirst(TreeDB dba, SettingsManipulator cman, IProgressMonitor mon) {
    loadRefTarget();
    return OperationResult.SUCEEDED;
  }

  @Override
  protected String[] getSettingsNames(TreeDB db) {
    return new String[]{LinksCacheMan.LATE_SECTION_NAME,
      NamesCacheMan.NAMES_SECTION_NAME,
      LinksCacheMan.REVERT_LINKS_SECTION_NAME};
  }
  private static String[] NONCP_DEFS = new String[]{"1.2.1","1.2.1","1.2.1"};
  @Override
  protected String[] getVersions() {
    return NONCP_DEFS;
  }

  public Map<UUID, Map<String, Map<TreeNode, String>>> getAllLinkedNodes(Collection<TreeNode> nodes) {
    List<RelationRecord> st = null;
    TreeDB dba = getRelatedTreeDB();
    CacheStorage cs = dba.getTracker().getCPCacheStorage(dba);
    cs.readLock(LinksCacheMan.REVERT_LINKS_SECTION_NAME);
    try{
      st = dba.getLinkResolver().getLCM().getDirectLinksForNodes(dba, nodes);
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

  public static void resetLinksObject(TreeNode curNode) {
    for(Attribute a:curNode.attrs()) {
      if(a.isReference()) {
        if(a.getType().equals(AttributeType.REFERENCE)) {
          resetValue(curNode, (ReferenceValue)a.getValue());
        }else if(a.getType().equals(AttributeType.LIST)) {
          resetListRefsLinks(curNode, (ListValue)a.getValue());
        }
      }
    }
  }
  private static void resetValue(TreeNode node, ReferenceValue value) {
    value.updateString(node.attrs());
  }

  private static void resetListRefsLinks(TreeNode node, ListValue value) {
    if(value.getType() == AttributeType.LIST) {
      for(Object lvo:value.getValues())
        resetListRefsLinks(node, (ListValue)lvo);
    }else
      for(Object rvo:value) {
        resetValue(node, (ReferenceValue) rvo);
      }
  }
}
