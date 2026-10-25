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

package com.unitesk.requality.core.model;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.unitesk.requality.core.api.IAttributeChangePropagator;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.IReqURIResolver;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.VersionsStorage;
import com.unitesk.requality.core.attribute.VersionsStorage.Type;
import com.unitesk.requality.core.attribute.valueprovider.CommonLabelProvider;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.DescModeValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.IAttributeFlagProvider;
import com.unitesk.requality.core.attribute.valueprovider.IAttributePropogationProvider;
import com.unitesk.requality.core.attribute.valueprovider.IGenericValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.attribute.valueprovider.TypeFilteredProvider;
import com.unitesk.requality.core.attribute.valueprovider.ValueProviderFactory;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.exception.CannotBeDeleted;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.exception.NullParentException;
import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.filter.IObjectFilter;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.instancer.TreeInstancer;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.CopyPasteHelper;
import com.unitesk.requality.core.operation.PasteIntoHandler;
import com.unitesk.requality.core.sorting.TreeNodeComparator;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.nodetype.NodeType;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.ReportGenerationContext;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.Index;
import com.unitesk.requality.tool.cli.RequalityCLI;

/** Class that represents element in requirements catalog. */
public class TreeNode implements Comparable<TreeNode>, ITreeNodeProvider {
  public static ChangesSet<Attribute> getIdEvent(Attribute old, Attribute newa){
    return new ChangesSet<>(
      Collections.emptySet(),
      AttributeStorage.formSet(TreeNode.ATTR_ID),
      Collections.emptySet(),
      AttributeStorage.formAttributeMap(old),
      AttributeStorage.formAttributeMap(newa),
      AttributeStorage.formList(TreeNode.ATTR_ID));
  }
//
//  public static AttributeDe formIdAttribute(UUID uid, String id){
//    return new Attribute(Version.getZero(), uid, ATTR_ID, id);
//  }

  public static final String ATTR_CHILDREN = "_workChildren";
  public static final String ATTR_CHILDREN_UUIDS = "_workChildrenUUIDs";
  public static final String ATTR_SHOWN_CHILDREN = "_shownChildren";
  public static final String ATTR_PARENT = "_parent";
  
  public static final String ATTR_CHILD_INDEX = "_childIndex";
  public static final String ATTR_POSITION = "_position";
  
  public static final String ATTR_TERMS = "_terms";
  public static final String ATTR_TERMS_REV = "_termsRev";
  public static final String ATTR_TYPE = "_type";
  public static final String ATTR_NAME = "_name";
  protected static String NumberRegex = "(-){0,1}\\d*";
  public static final String ATTR_REVERT_LINKS = "_revlinks";
  public static final String ATTR_PREDICATE = "_predicate";
  public static final String ATTR_FULL_PREDICATE = "_fullpredicate";
  public static final String ATTR_DESCRIPTION = "_description";
  public static final String ATTR_LOCATIONS_DESCRIPTION = "_ldescription";
  public static final String ATTR_GENERATORS = "_generators";
  public static final String NODE_GROUP = "com.unitesk.nodegroup.requality";
  // attribute for common undefined change over node
  public static final String ATTR_UNDEFINED = "_undefined";
  public static final String ATTR_ERROR = "_error";
  // public static final String ATTR_REVLINKS = "_%revlinks";
  public static final String ATTR_HIDDEN = VirtualNode.ATTR_HIDDEN_STATUS;
  public static final String ATTR_VIRT_REMOVED = "_virtRemoved";
  public static final String ATTR_IGNORE_NAME = "ignoreName";
  public static final String ATTR_NAMEORID = "_nameorid";
  public static final String ATTR_USERVISIBLEID = "_uservisibleid";
  public static final String ATTR_VIRT_STATUS = "_%virtstatus";
  public static final String ATTR_VIRT_CHILD_REMOVED = "_vchildremoved";
  public static final String ATTR_CLONE_PARENT = "_cloneparent";
  public static final String ATTR_ID = "_id";
  //public static final String ATTR_NODE_PATH = "node.resURL";
  public static final String ATTR_LINKS = "_links";
  public static final String ATTR_LABEL = "_label";
  public static final String ATTR_REPO_STATE = "_repoState";
  public static final String ATTR_INDEX_NOT_MANUAL = "_indexIsManual";
  private static final long DEFAULT_TIMELIMIT = 1000;
  private static final IValueProvider commonLabelVP = new CommonLabelProvider();
  private Map<String,AttributeDefinition> vdefs = new HashMap<>();
/*
  public Set<String> getLabelDeps(){
    if (db != null && !db.textIsSimple) {
      return Attribute.getHTMLNames();
    }
    return labelDeps;
  }
  
  public String formLabel(){
    if (db != null && !db.textIsSimple) {
      String desc = getDescription();
      if (desc != null && desc.length() > 5)
        return desc.substring(0, Math.min(desc.length(), 40)) + "\t[" + getId() + "]";
    }
    return getNameOrId();
  }*/
  private static final IValueProvider htmlLabelVP = new DescModeValueProvider();
  
  public static IMark[] NO_PROBLEMS = new IMark[0];
  protected IMark[] checkerProblems = NO_PROBLEMS;
  protected ConcurrentLinkedQueue<IMark> checkerTreeProblems = null;
  private CheckerContainer registeredInContainer = null;
  protected Boolean nodeLoaded = false;
  private Object loadSync; 
  private UUID uuid;
  private String uuidS = null;
  String vroot = null;
  private final TreeDB db;
  private TreeDB vdb = null;
  protected UUID cloneParent;
  //protected String id;
  UUID parent;
  
  private ReentrantReadWriteLock workChildLock = new ReentrantReadWriteLock();
  private WriteLock wl = workChildLock.writeLock();
  protected volatile ArrayList<UUID> workChildren = new ArrayList<>();
  //immutable cache of children uuids after visual struct modifiers to be shown to user
  //vistual struct modifiers are:
  //-hide by Predicate mechanics - hides node
  //-vnode collapse mechanics - adds vnode's children to shownChildren of its shownParent
  //protected List<UUID> shownChildren = null;

  private boolean disposed = false;
  private AttributeStorage attributes;
  
  protected ReadLock readWCLock(){
    ReadLock rl = workChildLock.readLock();
    rl.lock();
    return rl;
  }
  protected void readWCUnlock(ReadLock r){
    r.unlock();
  }
  public boolean writeWCLock(){
    if(wl.isHeldByCurrentThread())
      return false;
    wl.lock();
    return true;
  }
  public void writeWCUnlock(){
    wl.unlock();
  }
  public boolean hasCheckerProblems(Class c){
    IMark[]mrk = getCheckerProblems();
    if(mrk!=null){
      for(IMark m:mrk)
        if(m.getClass().isAssignableFrom(c))
          return true;
      return false;
    }
    return false;
  }
  private ReentrantLock treeCheckersLock = new ReentrantLock();
  
  public ReentrantLock getTreeCheckersLock() {
    return treeCheckersLock;
  }
  public IMark[] getTreeCheckerProblems(){
    return checkerTreeProblems == null? NO_PROBLEMS:checkerTreeProblems.toArray(new IMark[checkerTreeProblems.size()]);
  }
  public IMark[] getCheckerProblems(){
    ConcurrentLinkedQueue<IMark> warnings = getTreeDB().getWarnings(this);
    Collection<IMark> storageProblems = getTreeDB().getStorage().getStorageNodeProblems(uuid);
    IMark[] storedCheckerProblems = checkerProblems;
    ConcurrentLinkedQueue<IMark> storedCheckerTreeProblems = 
       checkerTreeProblems == null? null:
         new ConcurrentLinkedQueue<>(checkerTreeProblems);
    if(storedCheckerTreeProblems!=null || warnings!=null || storageProblems!=null || storedCheckerProblems.length>0){
      IMark[]ret = new IMark[storedCheckerProblems.length+
                             (storedCheckerTreeProblems!=null?storedCheckerTreeProblems.size():0)+
                             (warnings!=null?warnings.size():0) + (storageProblems == null?0:storageProblems.size())];
      int i = 0;
      for(IMark r:storedCheckerProblems)
        ret[i++]=r;
      if(storedCheckerTreeProblems!=null)
        for(IMark r:storedCheckerTreeProblems)
          ret[i++]=r;
      if(warnings!=null)
        for(IMark r:warnings)
          ret[i++]=r;
      if(storageProblems!=null)
        for(IMark r: storageProblems)
          ret[i++] = r;
      return ret;
    }
    return storedCheckerProblems;
  }
  public void removeTreeCheckerProblems(Class cl) {
    int cnt = 0;
    if(checkerTreeProblems == null)
      return;
    List<IMark> rem = new LinkedList<>();
    for(IMark m:checkerTreeProblems){
      if(m.getClass().isAssignableFrom(cl))
        rem.add(m);
    }
    for(IMark m:rem)
      checkerTreeProblems.remove(m);
  }
  public void clearTreeCheckerProblems() {
    if(checkerTreeProblems!=null)
      checkerTreeProblems.clear();
    checkerTreeProblems = null;
  }
  public void registerTreeCheckerProblems(Collection<IMark>problems) {
    if(problems!=null && problems.size()>0) {
      if(checkerTreeProblems == null)
        checkerTreeProblems = new ConcurrentLinkedQueue<>();
      for(IMark p:problems)
        checkerTreeProblems.add(p);
      getTreeDB().getCheckerContainer().registerProblems(this,getCheckerProblems());
    }
  }
  public void updateCheckerProblems() {
    TreeDB db = getTreeDB();
    if(db.revision==null || db.revision==RevisionInfo.NO_REV)
    {
      CheckerContainer cc = db.getCheckerContainer();
      checkerProblems = cc.gatherProblems(this);
      cc.registerProblems(this,getCheckerProblems());
    }
  }
  private IMark[] adaptStorageMarks(TreeNode treeNode,Collection<IMark> storageErrors){// TODO Auto-generated method stub
    return storageErrors.toArray(new IMark[0]);
  }
  public boolean canBeCheckedIn(CheckerContainer cc){
    if(db.revision!=null && db.revision!=RevisionInfo.NO_REV)
      return false;
    return registeredInContainer!=cc;
  }
  public void markToBeChecked(CheckerContainer cc){
    registeredInContainer = cc;
  }
  public void clearChecked(){
    registeredInContainer = null;
  }
  public String getForeignID() {
    return FIDUpdater.getForeignId(this);
  }
  public String getExternalForeignID() {
    return FIDUpdater.getExternalForeignId(this);
  }
  public String getNodeIdForReport(ReportGenerationContext context) {
    return IDMechanics.getNodeIdForReport(this, context);
  }
  public String getForeignNumber() {
    return FIDUpdater.getForeignNumber(this);
  }
  // link type(attr name)
  // private Map<TreeNode,Map<String,INodeChangeListener>>revlinks=null;


  protected boolean containsWorkChildUUID(UUID uid) {
    // if(outchildren==null)
    // getChildrenUUIds();
    ReadLock r = null;
    try{
      r = readWCLock();
      return workChildren.contains(uid);
    }
    finally{
      readWCUnlock(r);
    }
  }

  public String getMultiTypeLabel() {
    return getTypeLabel() + "s";
  }

  public final String getTypeLabel() {
    NodeType nt = db.getNodeType(getType());
    if (nt != null) return nt.getVisibleClassName();
    return "undefined";
  }
  protected void addChildUUID(UUID uid) {
    addChildUUID(uid, OFlag.NONE);
  }
  protected void addChildUUID(UUID uid, byte flag) {
    // getWorkChildrenUUIDs();
    if (uid == null) throw new RuntimeException("Attempt to add null child");
    if (uid.equals(getUUId())) {
      RLogger.logInfo("WARN! rec prevented " + getQualifiedId(true));
      throw new RuntimeException("Attempt to add self as child");
    }
    boolean wl = false;
    try{
      wl = writeWCLock();
      getChildrenRelatedVirtuals();
      if (!workChildren.contains(uid)) {
        if (getParentUUIDs().contains(uid)) 
          throw new RuntimeException("Error: detected attempt to one of ancestors to one of its descendant!");
        boolean b = workChildren.add(uid);
        if (b) {
//          clearShownChildren();
          if(!OFlag.isNoUpdate(flag)){
            updateLocalAttribute(TreeNode.ATTR_CHILDREN_UUIDS);
            getVS().updateVersion(Type.STRUCT);
            commitAttributes();
          }else
            getVS().updateVersion(Type.STRUCT);
          VirtualNode.updateClonesChildren(this);
        }
      }
    }
    finally{
      if(wl)
        writeWCUnlock();
    }
  }

  public boolean updateLocalAttribute(String attrChildrenUuids) {
    return attrs().updateAttribute(attrChildrenUuids);
  }
  private Set<UUID> getParentUUIDs() {
    Set<UUID> hs = new HashSet<>();
    hs.add(uuid);
    TreeNode par = getWorkParent();
    while (par != null) {
      hs.add(par.getUUId());
      par = par.getWorkParent();
    }
    return hs;
  }

  protected List<Object> getRemovedVirtualChilds() {
    List<Object> l = attrs().getListAttributeValue(ATTR_VIRT_CHILD_REMOVED);
    if (l == null) l = new LinkedList<>();
    return l;
  }

  protected void removeChildUUID(UUID uid) {
    removeChildUUID(uid, OFlag.NONE);
  }
  protected void removeChildUUID(UUID uid, byte flag) {
    // getWorkChildrenUUIDs();
    boolean b = false, w = false;
    try{
      w = writeWCLock();
      getChildrenRelatedVirtuals();//load current values for changes detection
      b = workChildren.remove(uid);
      // not vnode
    }finally{
      if(w)
        writeWCUnlock();
    }
    if (b && !isDisposed()) {
      //shownChildren = null;
      boolean commit = false;
      if(!OFlag.isNoUpdate(flag)){
        updateLocalAttribute(TreeNode.ATTR_CHILDREN_UUIDS);
        commit = true;
      }
      getVS().updateVersion(Type.STRUCT);
      if(commit)
        commitAttributes();
    }
    // if(isVirtual()||getType().equals(VirtualNode.TYPE_NAME))
    // VirtualNode.updateClonesChildren(this);
  }

  public void setChildrenUUIDs(List<UUID> children2, boolean init) {
      //shownChildren = null;
      boolean w = false;
      try{
        w = writeWCLock();
        boolean changes = setChildren(new ArrayList<>(children2), init);
      }
      finally{
        if(w)
          writeWCUnlock();
      }
  }

  public class TreeNodeChildIterator implements Iterator<TreeNode> {
    private int index;
    private boolean sh;

    public TreeNodeChildIterator(boolean shown) {
      sh = shown;
    }

    @Override
    public boolean hasNext() {
      return sh ? index < sizeShownChildren() : index < sizeWorkChildren();
    }

    @Override
    public TreeNode next() {
      return sh ? getShownChild(index++) : getWorkChild(index++);
    }

    @Override
    public void remove() {
      removeChild(sh ? getShownChild(index) : getWorkChild(index));
    }
  }

  /**
   * Elements can be grouped to support versions of storing formats for groups NODE_GROUP is related
   * to default group.
   *
   * @return identifier of node group
   */
  public String getGroup() {
    return NODE_GROUP;
  }

  public boolean isVirtual() {
    return cloneParent != null || getVirtState();
    // if(db == null)
    // return false;
    // return db.isVirtual();
  }

  private boolean getVirtState() {
    return attrs().getLocalStringAttributeValue(ATTR_VIRT_STATUS, null) != null;
  }

  /**
   * Method to get error messages appears during report generation
   *
   * @return error messages if any
   */
  public String getError() {
    //if (db.getStorage() instanceof TreeInstancer) db.updateNodeAttributes(this,,false);
    String s = attrs().getStringAttributeValue(ATTR_ERROR);
    if (s != null) return s;
    return "";
  }

  protected TreeNode(TreeDB db){
    this.db = db;
  }
  
  public TreeNode(TreeDB db, UUID uuid) {
    this(db);
    initVariables(db, uuid);
  }
  
  protected void initVariables(TreeDB db, UUID uuid){
    if (uuid == null) uuid = UUID.randomUUID();
    this.uuid = uuid;
    loadSync = uuid;//TODO can cause troubles?
    this.disposed = false;
  }

  /**
   * Method to set predicate that affect on node visibility in reports
   *
   * @param uuid2
   * @param pred predicate
   */
  public void setPredicate(String pred) {
    if (pred != null) {
      AttributeDefinition ad = new AttributeDefinition(getUUId(), ATTR_PREDICATE, new ConstValueProvider(AttributeType.STRING, pred));
//      Attribute a = attrs().createAttribute(ATTR_PREDICATE, pred);
  //    a.setAvailability(AttributeAvailability.SUBTREE);
      attrs().setAttribute(ad);
    } else attrs().removeAttribute(ATTR_PREDICATE);
  }

  /**
   * Method to get predicate that affect on node visibility in reports
   *
   * @return predicate
   */
  public String getPredicate() {
    return getPredicate(false);
  }

  public String getPredicate(boolean force) {
    // TODO handle versions
    // RLogger.logInfo(getQualifiedId()+" "+fullPredicate);
    String fullPredicate = null;
    StringBuilder sb = new StringBuilder();
    int addf = 0;
    //TODO proper predicate
    Attribute atrs = attrs().getLocalAttribute(ATTR_PREDICATE);
    String[] tolook = new String[3];
    if (atrs != null) {
      if (atrs.getOrigin().equals(getUUId())
          && atrs.getValue() != null
          && !atrs.getValue().toString().isEmpty())
        tolook[0] = attrs().getStringAttributeValue(atrs.getKey(), null);
      TreeNode par = getWorkParent();
      if (par != null) tolook[2] = par.getPredicate();
      TreeNode cp = getCloneParent();
      if (cp != null) tolook[1] = cp.getPredicate();
      for (int i = 0; i < 3; i++) {
        String sval = tolook[i];
        if (sval != null && !sval.isEmpty())
          if (sb.length() == 0) sb.append(sval);
          else {
            addf++;
            sb.append(")&&(");
            sb.append(sval);
            sb.append(")");
          }
      }
    }
    fullPredicate = sb.toString();
    sb.setLength(0);
    for (int i = 0; i < addf; i++) sb.append("(");
    sb.append(fullPredicate);
    return sb.toString();
  }

  public TreeNode getCloneParent() {
    UUID uid = getCloneParentUUId();
    if (uid == null || uid.equals(uuid)) {
      // TODO clonep?
      return null;
    }
    try {
      return db.getNodeExc(uid);
    } catch (NodeNotFoundException e) {
    }
    return null;
  }

  public UUID getCloneParentUUId() {
    if (cloneParent != null) return cloneParent;
    return null; // VirtualNode.getVirtualStorage(db).getCloneParent(uuid);
  }

  /**
   * Method to set type of element in form of string Types names are usually was in constants like
   * Requirement.TYPE_NAME
   *
   * @param type string name of type
   */
  public void setType(String type) {
    setType(type, false);
  }
  public void setType(String type, boolean inner) {
    if(getUUId()!=null)
      attrs().setAttribute(new AttributeDefinition(getUUId(), ATTR_TYPE, 
        new ConstValueProvider(AttributeType.STRING, type), Flag.HIDDEN), inner);
    //attrs().setAttribute(ATTR_TYPE, type);
  }

  /**
   * get UUID - unique identificator over element.
   *
   * @link java.util.UUID
   * @return uuid of element
   */
  public final UUID getUUId() {
    return uuid;
  }

  public final String getSUUId() {
    if(uuidS==null)
      uuidS = uuid.toString();
    return uuidS;
  }
  /**
   * Set identifier of element. Id is identical to name of corresponding file on storage. Needs to
   * be unique for siblings. Id also can form indexes that can be used in sorting.
   *
   * @param id new identifier of element.
   */
  public void setId(String id) {
    setId(id, false);
  }
  public void setId(String id, boolean inner) {
    id = id.replace((char) 160, ' ');
    attrs().setAttribute(formIdAttribute(uuid, id), inner);
  }

  /**
   * Get identifier of element. Id is identical to name of corresponding file on storage. Needs to
   * be unique for siblings. Id also can form indexes that can be used in sorting.
   *
   * @return current identifier of element.
   */
  public String getId() {
    // if(isVirtual()&&cloneParent==null){/
    // return VirtualNode.getVirtualStorage(db).getNodeId(uuid);
    // }
    // cannot be true for today
    Attribute old = attrs().getAttribute(ATTR_ID);
    if(old == null)
      return null;
    if(old.getValue() == null)
      return null;
    return old.getValue().toString();
    //return id;
  }

  public String getRawId() {
    return attrs().getAttribute(ATTR_ID).getValue().toString();
  }

  /** @return String name of node type */
  public String getType() {
    return attrs().getStringAttributeValue(ATTR_TYPE, "undefined");
  }

  /**
   * Set parent element
   *
   * @param parent new parent element
   */
  public void setParent(TreeNode parent) {
    if (isVirtual() && cloneParent == null) {
    } else {
      TreeNode par = getWorkParent();
      TreeNode targ = this;
      TreeNode newParent = parent;
      if (isVirtual()) {
        if (!parent.isVirtual()) { // TODO is it needed?
          TreeNode np = par;
          if (VirtualNode.TYPE_NAME.equals(np.getType())) {
            if (((VirtualNode) np).isCollapsed()) {
              if (np.getWorkChildrenUUIDs().size() == 1) {
                targ = np;
              }
            }
          }
          if (targ == this) {
            // masterDB = VirtualNode.getRootTree(db);
            CopyPasteHelper cph = CopyPasteHelper.getInstance();
            List<TreeNode> nodes = new ArrayList<>();
            List<NodeDesc> ns = cph.getNodes();
            nodes.add(this);
            cph.setNodes(nodes);
            cph.getNodes().get(0).removeAttribute(ATTR_VIRT_STATUS);
            PasteIntoHandler pih = new PasteIntoHandler();
            Object ret = pih.pasteNodes(newParent, null, true, true, true, true, true);
            if (ret instanceof String) throw new RuntimeException("Cannot change parent " + ret);
            TreeNode[] nodes2 = (TreeNode[]) ret;
            if (parent.isVirtual())//TODO test?
              (parent.getTreeDB().getStorage()).moveNodeTo(uuid, parent.getUUId(), OFlag.NONE);
            cph.setNodesDescs(ns);
            attrs().setConstAttribute(ATTR_VIRT_REMOVED, true, Flag.HIDDEN);
            //TODO del?
            // TODO error to show
            commitAttributes();
            if (nodes2 == null) return;
          }
        } else {
        }
      }
      db.moveNode(targ, parent);
    }
  }

  void fireGUINodeMoved(TreeNode oldParent) {
    ReadLock rl = listenersLock.readLock();
    rl.lock();
    if (guilistenerList != null){
      List<INodeChangeListener> list = new LinkedList<>(guilistenerList);
      rl.unlock();
      for (INodeChangeListener i : list) i.moved(this, oldParent);
    }else
      rl.unlock();
  }

  void fireNodeMoved(TreeNode oldParent) {
    ReadLock rl = listenersLock.readLock();
    rl.lock();
    if (listenerList != null)
    {
      List<INodeChangeListener> list = new ArrayList<>(listenerList);
      rl.unlock();
      for (INodeChangeListener i : list)
        i.moved(this, oldParent);
    }else
      rl.unlock();
  }

  /**
   * Get unique identifier of parent element. It does not cause parent element loading.
   *
   * @return unique identifier of parent element
   */
  public UUID getWorkParentUUID() {
    return parent;
  }
  public UUID getShownParentUUID() {
    try{
      return getShownParentUUIDExc(); // TODO error??
    }catch(NodeNotFoundException e) {
    }catch(Exception e) {
      RLogger.logError(e);
    }
    return null;
  }
  public UUID getShownParentUUIDExc() {
    return getShownParentUUIDExc(db, getUUId(), parent);
//    TreeNode node = getShownParentExc(); // TODO change?
//    if (node != null) return node.getUUId();
//    if (parent == null) return null;
//    return null; // TODO error??
  }
  public void removeChild(TreeNode node) {
    removeChild(node,OFlag.NONE);
  }

  /**
   * Remove child element from current
   *
   * @param node element to remove
   */
  public void removeChild(TreeNode node, byte flag) {
    if(!db.getTreeLogic().canBeDeleted(node)&&OFlag.isNoUpdate(flag))
      throw new CannotBeDeleted(node);
    db.startChangeRow();
    try {
      //removeChildUUID(node.getUUId());

      // db.fireNodeGUIRemoved(node,NodeDesc.fromNode(node,false,false),
      // true);//TODO look into
      if (node.isVirtual()) {
        String uid = node.getVRoot();
        TreeDB rdb = Instantiator.getRootTree(this.db, TreeDB.DEFAULT_DEPTH);
        if (uid != null) {
          TreeDB db = Instantiator.getExistVirtualTreeDB(this.db, uid);
          if (db != null) db.removeNode(node, flag);
          else rdb.removeNode(node, flag);
        } else rdb.removeNode(node, flag);
        // Instantiator.getVirtualTreeDB(db,Instantiator.DEFAULT_INSTANCE_NAME).removeNode(node);
      } else db.removeNode(node, flag);
      // VirtualNode.updateClonesChildren(this);
    } finally {
      db.stopChangeRow();
    }
  }

  /**
   * Set text description for element. As an example may be description of requirements.
   *
   * @param description new description of element
   */
  public void setDescription(String description) {
    if (description == null || description.equals("")) attrs().removeAttribute(ATTR_DESCRIPTION);
    else{
      attrs().setAttribute(new AttributeDefinition(getUUId(), ATTR_DESCRIPTION, AttributeAvailability.LOCAL,
          new HtmlValueProvider(AttributeType.HTML, description, TempFolderFilesProvider.getNewNotInited(AttributeType.HTML)),Flag.HIDDEN));
      //attrs().setAttribute(ATTR_DESCRIPTION, description);
    }
  }
  public void setDescription(HtmlValueProvider hvp) {
    if (hvp == null) attrs().removeAttribute(ATTR_DESCRIPTION);
    else{
      attrs().setAttribute(new AttributeDefinition(getUUId(), ATTR_DESCRIPTION, AttributeAvailability.LOCAL,
          hvp,Flag.HIDDEN));
      //attrs().setAttribute(ATTR_DESCRIPTION, description);
    }
  }
  /**
   * Get current text description for element. As an example may be description of requirements.
   * This method returns description prepared for usage in reports. Picture paths are replaced to
   * corresponding paths in report folder.
   *
   * @param prefix optional prefix for description
   * @param postfix optional postfix for description
   * @param basePath path to report folder
   * @param rslv resolver for links
   * @return description prepared to be used in report
   */
  public String getADescription(String basePath) {
    return getADescription("<div>", "</div>", basePath, null);
  }

  public String getADescription(String prefix, String postfix, String basePath, IReqURIResolver rslv) {
    String desc = getDescription(false, false, false);//TODO look
    if (desc == null) return null;
    try {
      URI rp = getNodeResourcesPath();
      if(rp == null)
        RLogger.logDebug("Resource path is null for node "+getSUUId()); 
      else
        return CoreUtils.resolveURI(this, rslv, RequalityCLI.getReportDescription(
          this,TreeNode.ATTR_DESCRIPTION, rp.toString(), desc, prefix, postfix, basePath));
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return desc;//fallback (res not loaded)
  }

  public String getLocatonsDescription(String base) {
    return getLocatonsDescription("<div>", "</div>", base, " ", null);
  }

  public String getDescription(String prefix, String postfix, String basePath, IReqURIResolver reslv) {
    return getDescription(prefix, postfix, basePath, " ",reslv);
  }

  public String getDescription(String prefix, String postfix, String basePath, String separator,
      IReqURIResolver reslv) {
    String desc = getADescription(prefix, postfix, basePath, reslv);
    if (desc == null) {
      desc = getLocatonsDescription(prefix, postfix, basePath, separator, reslv);
      if (desc == null) return null;
      try {
        URI path = getNodeResourcesPath();
        return CoreUtils.htmlEncapsulate(prefix+
            RequalityCLI.getReportDescription(
                this, TreeNode.ATTR_DESCRIPTION, path != null ? path.toString() : null, desc, prefix, postfix, basePath)+postfix);
      } catch (Exception e) { // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return null;
    }
    return CoreUtils.htmlEncapsulate(prefix+desc+postfix);
  }

  public String getLocatonsDescription(
      String prefix, String postfix, String basePath, String separator,
      IReqURIResolver rslv) {
    Attribute locs = attrs().getAttribute(Requirement.ATTR_LOCATIONS);
    if (locs != null && locs.getValue() != null) {
      List<?> res = ((ListValue) locs.getValue()).getValues();
      StringBuilder sb = new StringBuilder();
      Iterator<?> it = res.iterator();
      while (it.hasNext()) {
        ReferenceValue s = (ReferenceValue) it.next();
        if(s.hasErrorsOnResolve(attrs()))
          continue;
        TreeNode nd = s.getNodes(attrs())[0];
        if (nd != null) {
          String desc = nd.getDescription(prefix, postfix, basePath, separator,rslv);
          if(desc!=null)
            sb.append(CoreUtils.resolveURI(this, rslv,desc));
          if (it.hasNext() && !sb.toString().endsWith(separator)) sb.append(separator);
        }
      }
      return sb.toString();
    }
    return null;
  }

  public String getLocatonsDescription(boolean process, String separator, boolean external) {
    Attribute locs = attrs().getAttribute(Requirement.ATTR_LOCATIONS);
    if (locs != null && locs.getValue() != null) {
      List<?> res = ((ListValue) locs.getValue()).getValues();
      StringBuilder sb = new StringBuilder();
      Iterator<?> it = res.iterator();
      while (it.hasNext()) {
        ReferenceValue s = (ReferenceValue) it.next();
        if(s.hasErrorsOnResolve(attrs()))
          continue;
        TreeNode nd = s.getNodes(attrs())[0];
        if (nd != null) {
          sb.append(nd.getDescription(process, false, external));
          if (it.hasNext()) sb.append(separator);
        }
      }
      return sb.toString();
    }
    return null;
  }

  /**
   * Get current text description for element. As an example may be description of requirements.
   * This method returns description prepared for usage in reports. Picture paths are replaced to
   * corresponding paths in report folder.
   *
   * @param basePath path to report folder
   * @return description prepared to be used in report
   */
  public String getDescription(String basePath) {
    return getDescription(basePath, null);
  }
  public String getDescription(String basePath, IReqURIResolver rslv) {
    String result = getDescription("<div>", "</div>", basePath,rslv);
    return result;
  }

  /**
   * Return element description to be shown in uni editor and properties panels. Paths for
   * resources(for ex. images) are not processed
   *
   * @return element description
   */
  public String getDescription() {
    return getDescription(false, false, false);
  }

  public String getExternalDescription() {
    return getDescription(false, false, true);
  }
  
  /**
   * Return element description to be shown in uni editor and properties panels
   *
   * @param process process resource path flag
   * @param useLocs TODO
   * @return element description
   */
  public String getDescription(boolean process, boolean useLocs, boolean external) {
    String desc = attrs().getStringAttributeValue(ATTR_DESCRIPTION);
    String ret = null;
    if (desc != null) {
      ret = desc;
    } else if (useLocs) ret = getLocatonsDescription(process, " ", external);
    if (process && ret != null) {
      ret = processResourcesPath(ATTR_DESCRIPTION, ret, external);
    }
    return ret;
  }

  public String getRawDescription() {
    return getRawDescription(null);
  }
  
  
  /**
   * Get current text description for element. This method returns description prepared for usage in reports. 
   * Object paths are replaced to corresponding paths in report folder.
   * Requality links are replaced to corresponding anchor link in report.
   *   
   * @param process resource path flag
   * @param rev resolver for links
   * @return element description
  **/
  public String getRawDescription(IReqURIResolver rev) {
    Attribute desc = attrs().getAttribute(ATTR_DESCRIPTION);
    String ret = null;
    if (desc != null && desc.getDefinition().getRawValue()!=null && desc.getDefinition().getRawValue().getValue()!=null) {
      ret = desc.getDefinition().getRawValue().getValue().toString();
    }
    if (rev != null) {
      ret = CoreUtils.resolveURI(this, rev, ret);
    }
        
    return ret;
  }
  

  public String getRawDescription(String prefix, String postfix, String basePath,
      IReqURIResolver reslv) {
    String separator = " ";
    String desc = getRawDescription(reslv);
    if (desc == null)
      return null;
    try {
      URI path = getNodeResourcesPath();
      return CoreUtils.htmlEncapsulate(prefix+
          RequalityCLI.getReportDescription(
              this, TreeNode.ATTR_DESCRIPTION, path != null ? path.toString() : null, desc, prefix, postfix, basePath)+postfix);
    } catch (Exception e) { // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return CoreUtils.htmlEncapsulate(prefix+desc+postfix);
  }

  /**
   * Process paths in description, replace placeholder {node.resURL} to actual path to images
   *
   * @param desc description of element
   * @return description of element with correct path to resources of node
   */
  public String processResourcesPath(String key, String desc, boolean external) {
    URI resPath = getNodeResourcesPath();
    if (resPath != null && desc!=null) {
      int pos = desc.indexOf(ResManager.RES_VAR);
      while (pos > -1) {
        char q = '"';
        byte mode = 0;
        int apos = pos;
        while (mode == 0 && apos > 0) {
          apos--;
          char c = desc.charAt(apos);
          if (c == '"' || c == '\'') {
            q = c;
            mode = 1;
          }
        }
        if (mode == 0) {
          apos = -1;
          pos = desc.length();
        } else pos = desc.indexOf(q, pos);
        if (pos > -1) {
          String path =
              desc.substring(apos + 1, pos)
                  .replace(ResManager.RES_VAR, resPath.toString())
                  .replace("\\", "/");
          desc = desc.substring(0, apos + 1) + path + desc.substring(pos);
          pos = desc.indexOf(ResManager.RES_VAR);
        }
      }
      // result = desc.replace("", resPath);
    }
    if(key == null)
      resPath = getNodeResourcesPath();
    else {
      if(external)
        resPath = getExternalNodeResourcesPath(key);
      else
        resPath = getNodeResourcesPath(key);
    }
    if (resPath != null && desc!=null) {
        int pos = desc.indexOf(ResManager.ATTR_RES_VAR);
        while (pos > -1) {
          char q = '"';
          byte mode = 0;
          int apos = pos;
          while (mode == 0 && apos > 0) {
            apos--;
            char c = desc.charAt(apos);
            if (c == '"' || c == '\'') {
              q = c;
              mode = 1;
            }
          }
          if (mode == 0) {
            apos = -1;
            pos = desc.length();
          } else pos = desc.indexOf(q, pos);
          if (pos > -1) {
            String path =
                desc.substring(apos + 1, pos)
                    .replace(ResManager.ATTR_RES_VAR, resPath.toString())
                    .replace("\\", "/");
            desc = desc.substring(0, apos + 1) + path + desc.substring(pos);
            pos = desc.indexOf(ResManager.ATTR_RES_VAR);
          }
        }
        // result = desc.replace("", resPath);
      }
    return desc;
  }

  /**
   * Get tree database in which this element exists
   *
   * @return tree database
   */
  public TreeDB getTreeDB() {
    if(vdb!=null)
      return vdb;
    vdb = db;
    if (db!=null && !db.isVirtual() && getVRoot() != null) vdb = Instantiator.getExistVirtualTreeDB(db, getVRoot());
    return vdb;
  }

  /**
   * Set unique identifier of element
   *
   * @link java.util.UUID
   * @param uuid new unique identifier
   */
  void setUUId(UUID uuid) {
    this.uuid = uuid;
    uuidS = null;
  }

  public TreeNode getShownParent() {
    try{
      return getShownParentExc();
    }
    catch(NodeNotFoundException e){//if cannot load then return null;
      RLogger.logDebug(CoreUtils.getTrace(e));
    }
    return null;
  }
  /**
   * Get parent of current element. Causes element loading if it is not in tree database.
   *
   * @return parent element
   */
  public TreeNode getShownParentExc() throws NodeNotFoundException, NullParentException {
    UUID parentUUID = getShownParentUUIDExc(db, getUUId(), parent);
    if(parentUUID == null)
      return null;
    TreeNode parent = null;
    if(isDisposed()) 
      parent = db.getCachedNodeOrVirt(parentUUID); 
    else
      parent = db.getNodeExc(parentUUID);
    if (parent!=null && VirtualNode.TYPE_NAME.equals(parent.getType())
        && parent.getCloneParent() == null
        && (parent.isVirtual() || db.isVirtual())) {
      try {
        TreeNode nde2 = Instantiator.getRootTree(db).getNodeExc(parent.getUUId());
        if (nde2 != null) return nde2;
      } catch (Exception e) {
        e.printStackTrace();
      }
    } 
    return parent;
  }
  public static UUID getShownParentUUIDExc(TreeDB db, UUID node, UUID parentUUID) throws NodeNotFoundException, NullParentException {
    //TODO move to virtual attribute
    UUID parent = null;
    TreeNode cached = db.getCachedNodeOrVirt(node);
    if(cached == null)
    {
      parent = db.getStorage().getParent(node);
      if(parent == null)
        parent = parentUUID;
    }else
    {
      parent = cached.parent;
    }
    if (parent == null) {
      if(isRoot(db, node))
        return null;
      else
        throw new NullParentException(node, db.getSemiLocalName());
        //RLogger.logError("Null parent uuid on node "+node + " "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
    }
    //TreeNode node = null;
    if (db.isNodeExist(parent)
        && VirtualNode.TYPE_NAME.equals(db.getNodeType(parent))) {
      boolean find = false;
      Object clps = db.getAttributeValue(parent, VirtualNode.ATTR_COLLAPSEVNODE, false, false);
      if (clps == null || clps.toString().equals("true")) find = true;
      if (find) 
        return getShownParentUUIDExc(db, parent, null);
      else 
        return parent;
    } else
      return parent;
  }

  /**
   * Get parent of current element. Causes element loading if it is not in tree database.
   *
   * @return parent element
   */
  public TreeNode getWorkParent() {
    if (parent == null) {
      // no parent
//      assert this instanceof NodeType 
//          ||db == null
//          || db.getRootNodeUUID() == null
//          || db.getRootNodeUUID().equals(this.getUUId());
      return null;
    }
    try {
      TreeNode node = null;
      try {
        if (parent != null) node = db.getNode(parent);
        else return null;

      } catch (RuntimeException re) {
        RLogger.logInfo("cannot load parent for "+uuid+" "+CoreUtils.getTrace(re));
      }
      return node;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Get children count for current element.
   *
   * @return children count
   */
  public int sizeShownChildren() {
    return sizeShownChildren(false);
  }

  public int sizeShownChildren(boolean onlyloaded) {
    //if (shownChildren == null) getShownChildrenUUIDs(onlyloaded);
    //if (shownChildren != null) return shownChildren.size();
    return ((ListValue)attrs().getAttribute(ATTR_SHOWN_CHILDREN).getGValue()).getValues().size();
    //throw new RuntimeException("Shown children cannot be formed!");
    // return 0;
    // return getChildren().size();
  }

  public TreeNode getShownChild(int i) {
    return getShownChild(i, false); // TODO think
  }

  /**
   * Get child element with index i in children uuid list
   *
   * @param i index of child in uuid list
   * @param onlyloaded
   * @return child element
   */
  public TreeNode getShownChild(int i, boolean notcheck) {
    return getShownChild(i, notcheck, false);
  }

  public TreeNode getShownChild(int i, boolean notcheck, boolean onlyloaded){
    ListValue lv = attrs().getAttribute(ATTR_SHOWN_CHILDREN).getGValue();
    return getTreeDB().getNode((UUID)(lv.getValues().get(i)));
//    if (shownChildren == null) getShownChildrenUUIDs(onlyloaded);
//    TreeNode node = null;
//    if (shownChildren != null)
//      if (onlyloaded) node = db.getCachedNodeOrVirt(shownChildren.get(i));else 
//      try {
//        node = db.getNode(shownChildren.get(i), TreeDB.DEFAULT_DEPTH, notcheck);
//      } catch (NodeNotFoundException e){// TODO Auto-generated catch block
//        RLogger.logInfo(CoreUtils.getTrace(e));
//      }
//    else throw new RuntimeException("Shown children cannot be formed!");
//    // node =
//    // db.getNode(getChildren().get(i),TreeDB.DEFAULT_DEPTH,notcheck);
//    if (node != null && node.isVirtual() && !db.isVirtual()) {
//      UUID uid = node.getVRoot();
//      if (onlyloaded)
//        return Instantiator.getExistVirtualTreeDB(db, uid).getCachedNodeOrVirt(node.getUUId());
//      else return Instantiator.getExistVirtualTreeDB(db, uid).getNode(node.getUUId());
//    }
//    return node;
  }

  public TreeNode getRoot() {
    String vr = getVRoot();
    if (vr != null) {
      if (vr.equals(getSUUId())) return this;
      return db.getNode(UUID.fromString(vr));
    }
    return db.getRootNode();
  }

  public boolean isVRoot() {
    // see treeDb.getnode
    String vr = getVRoot();
    if (vr != null) return getSUUId().equals(vr);
    return Instantiator.isVRoot(db, uuid, attrs().getLMap());
    // ITreeStorage storage = getTreeDB().getStorage();
    // UUID par = storage.getParent(uuid);
    // if(par!=null && storage.getNodeId(par,true).equals("Instances")){
    // par = storage.getParent(par);
    // if(par!=null&&storage.getRootNode().equals(par))
    // return true;
    // }
    // return false;
  }

  /**
   * Get path to element in form of identifier(id) of element and all its ancestors in form of
   * /RootId/.../ElementParentId/ElementId
   *
   * @return path to element
   */
  public String getQualifiedId() {
    return getQualifiedId(false);
  }
  public String getQualifiedId(boolean inner) {
    StringBuilder sb = new StringBuilder();
    getQualifiedId(sb, new HashSet<TreeNode>(), inner);
    return sb.toString();
  }

  private void getQualifiedId(StringBuilder sb, HashSet<TreeNode> hashSet, boolean inner) {
    hashSet.add(this);
    if (!inner && isDisposed()) return;
    //UUID paruid = getWorkParentUUID();
    TreeNode par = null;
    try{
       par = db.getNodeExc(parent);
    }
    catch(NodeNotFoundException e){
      //cutted subtree
      return;
    }
    if (hashSet.contains(par)) {
      RLogger.logInfo("recursion error on qid call for "+uuid);
      return;
    }
    if (par == null || isVRoot()) {
      sb.append('/');
      return;
    }
    par.getQualifiedId(sb, hashSet, inner);

    /* append '/' if sb != "/" */
    if (sb.length() != 1) {
      sb.append('/');
    }
    sb.append(getId());
  }

  /**
   * Method to locate child with given id. Return null if it is not found.
   *
   * @param id id of child to find
   * @return child element
   */
  public TreeNode findWorkChild(String id) {
    id = id.replace((char) 160, ' ');
    if (id.startsWith("|:") && id.endsWith(":|")) { // child num
      int num = Integer.parseInt(id.substring(2, id.length() - 2));
      if (num < sizeWorkChildren()) {
        return getSortedWorkChildren().get(num);
      }
      return null;
    }

    List<UUID> uids = getWorkChildrenUUIDs();
    for (UUID uid : uids) {
      try {
        TreeNode child = db.getNode(uid, TreeDB.DEFAULT_DEPTH, false);
        if (child.getRawId().equals(id)) {
          return child;
        }
        if (child.getId().toLowerCase().equals(id.toLowerCase())) {
          return child;
        }
        if (child.getSUUId().equals(id)) return child;
      } catch (Exception e) {
        //e.printStackTrace();
      }
    }
    return null;
  }

  /**
   * Get list of children UUIDs
   *
   * @return list of children UUIDs
   */
  public static String join(String r[], String d) {
    //TODO String.join(d, r); ?
    if (r.length == 0) return "";
    StringBuilder sb = new StringBuilder();
    int i;
    for (i = 0; i < r.length - 1; i++) sb.append(r[i] + d);
    return sb.toString() + r[i];
  }
//  public List<UUID> getShownChildrenUUIDs(boolean onlyloaded) {
//    // if(shownChildren!=null &&
//    // !getVS().isParentVersionOutdated(Type.ATTRIBUTES))
//    // return shownChildren;
//
//    List<UUID> ch2 = new ArrayList<>();
//    @SuppressWarnings("unused")
//    boolean vchild = false;
//    List<UUID> childs = new LinkedList<>(getWorkChildrenUUIDs(onlyloaded));
//    for (UUID uid : childs) {
//      if (uid == null) continue;
//      if (getTreeDB().isNodeExist(uid)) {
//        String type = getTreeDB().getNodeType(uid);
//        if (type == null && !getTreeDB().isVirtual())
//          for (TreeDB db : Instantiator.getVirtualTreeDBs(getTreeDB())) {
//            type = db.getNodeType(uid);
//            if (type != null) break;
//          }
//        if (type != null && VirtualNode.TYPE_NAME.equals(type)) {
//          VirtualNode node = (VirtualNode) getTreeDB().getNode(uid);
//          if (!node.isCollapsed()) ch2.add(uid);
//          else {
//            vchild = true;
//            ch2.addAll(node.getWorkChildrenUUIDs(onlyloaded));
//            ; // TODO mb shown here?
//          }
//        } else ch2.add(uid);
//      }
//    }
//    if (
//    /* (isVirtual()||vchild)&& */ ch2.size() > 0 && !isVRoot()) {
//      ch2 = TreeDB.updateVisablility(getTreeDB(), ch2);
//    }
//    shownChildren = Collections.unmodifiableList(ch2); 
//    return ch2;
//  }


  public String getLabel() {
    Attribute a = attrs().getAttribute(ATTR_LABEL);
    if(a!=null && a.getValue()!=null)
      return a.getValue().toString();
    return null;
  }
  
  public IValueProvider getLabelVP(){
    if (db != null && !db.getLabelIsSimple()) {
      return htmlLabelVP;
    }
    return commonLabelVP;
  }
  
  @Override
  public String toString() {
    return getLabel();
  }

  /**
   * Create child element with given id and type "Node"
   *
   * @param id id of created element
   * @return new child element
   */
  public TreeNode createChild(String id) {
    if (findWorkChild(id) != null)
      throw new IllegalArgumentException("element with id '" + id + "' already exists");
    id = id.replace((char) 160, ' ');
    //shownChildren = null;
    if (getType().equals("DBRoot")) {
      TreeNode node = db.createChildNode(uuid, id, "Node");
      return node;
    }
    TreeNode node = db.createChildNode(uuid, id, getType());
    // addChildUUID(node.getUUId());//TODO may be return
    return node;
  }

  public TreeNode createChild(String id, String type) {
    return createChild(id, type, null);
  }

  /**
   * Create child element with given id and type type
   *
   * @param id id of created element
   * @param templ
   * @type string type of element
   * @return new child element
   */
  public TreeNode createChild(String id, String type, String templ) {
    // TODO merge methods
    if (findWorkChild(id) != null)
      throw new IllegalArgumentException("element with id '" + id + "' already exists");
    id = id.replace((char) 160, ' ');
    //shownChildren = null;
    TreeNode node = db.createChildNode(uuid, id, type, templ);
    return node;
  }
  //TODO mb remove or refactor
  public TreeNode createChild(UUID parent, UUID uid, String id, String templ, Map<String, AttributeDefinition> attrs, byte flag) {
    if (findWorkChild(id) != null)
      throw new IllegalArgumentException("element with id '" + id + "' already exists");
    id = id.replace((char) 160, ' ');
    //shownChildren = null;
    TreeNode node = db.createChildNode(NodeDescSet.fromNodeDesc(new NodeDesc( uid, parent, id, attrs, false),true), templ, flag);
    return node;
  }

  public void delete() {
    delete(OFlag.NONE);
  }
  /** Delete this element */
  public void delete(byte flag) {
    if(!OFlag.isNoUpdate(flag)/*initial update call*/&&!db.getTreeLogic().canBeDeleted(this))
      throw new CannotBeDeleted(this);
    if (getWorkParent() != null) 
      getWorkParent().removeChild(this,flag);
    else {
      db.removeNode(this, flag);
    }
  }

  // private void updateDirectCloneAttributes(Map<String, Attribute> adiff) {
  // if(uuid!=null && db!=null && db.getStorage()!=null &&
  // uuid!=db.getRootNodeUUID()&&!isVirtual()
  // &&!VirtualNode.getVirtualStorage(getTreeDB()).hasCloneParent(uuid)){
  // TreeNode clone = VirtualNode.getVirtualTreeDB(getTreeDB()).getNode(uuid);
  // Map<String,Attribute> attrs = clone.attributes;
  // for(String s : attrs.keySet()){
  // if(!attrs.containsKey(s)){
  // attrs.put(s, new LinkedList<Attribute>(attributes.get(s)));
  // }else
  // if(!attributes.containsKey(s))
  // attrs.remove(s);
  // else
  // attrs.put(s, new LinkedList<Attribute>(attributes.get(s)));
  // }
  // }
  // }
  //
  public boolean affectsPredicate(String what) {
    //TODO move to predicate
    if (what.equals(ATTR_PREDICATE)) return true;
    String pred = getPredicate();
    if (pred != null && !pred.isEmpty()) {
      // for(String s:attributes.getRevertDependencies(ATTR_PREDICATE))
      if (pred.indexOf(what) > -1) return true;
    }
    return false;
  }

  public AttributeDefinition alterAttributeBeforeChange(String s, AttributeDefinition ad) {
    if (s.equals(ATTR_PREDICATE)) {
        if (ad == null || ad.getRawValue() == null || ad.getRawValue().getGValue().equals(""))
          return null;
        //
      }
      if (s.equals(ATTR_DESCRIPTION)) {
          TypedValue nw = null;
          if (ad != null) nw = ad.getRawValue();
          if (ad == null || (nw == null || nw.getValue() == null || nw.getValue().equals(""))) {
            return null;
          }
          // if(desc.contains(""))
        }

     if (s.equals(ATTR_NAME)) {
          if(ad == null || ad.getRawValue() == null ||
              ad.getRawValue().getValue() == null ||
              ad.getRawValue().getValue().toString().equals("")) {
              return null;
          }
     }
     return ad;
  }
  protected boolean typeRelatedAttributeChangeReactionBefore(ChangesSet<Attribute> what, byte flag) {
    return false;
  }
  public final void reactBeforeAttributeChange(
      ChangesSet<Attribute> what, byte flag) {
    TreeNode nde = null;
    boolean cp = false;
    nde = getShownParent();
    if(typeRelatedAttributeChangeReactionBefore(what, flag))
      cp = true;
    if(cp && nde!=null)
      nde.commitAttributes();
  }
  protected boolean typeRelatedAttributeChangeReactionAfter(ChangesSet<Attribute> what, byte flag) {
    return false;
  }
  public final void reactAfterAttributeChange(
      ChangesSet<Attribute> what, byte flag) {
    if (what.contains(TreeNode.ATTR_CLONE_PARENT)) {
      Object nw = null;
      Attribute attribute = what.getNew(TreeNode.ATTR_CLONE_PARENT);
      if (attribute != null) nw = attribute.getDefinition().getRawValue().getValue();
      if (attribute != null && nw != null) cloneParent = UUID.fromString(nw.toString());
    }
    if (what.contains(ATTR_VIRT_STATUS)) {
      Object nw = null;
      Attribute attribute = what.getNew(ATTR_VIRT_STATUS);
      if (attribute != null) nw = attribute.getValue();
      //TODO drop db virt status
      vroot = null;
      if (attribute != null && nw != null) vroot = nw.toString();
    } 
    TreeNode nde = null;
    boolean cp = false;
    boolean parCh = false;
    if (what.contains(ATTR_HIDDEN)) {
      nde = getShownParent();
      if (nde != null) {
        // error(
        //TODO move to VP
        //TODO updateLocalattribute?
        nde.updateLocalAttribute(TreeNode.ATTR_SHOWN_CHILDREN);
        parCh = true;
      }
    }
    boolean ap = false;
    for(String s:what.getAll())
      if(affectsPredicate(s))
        ap = true;
    if (what.contains(ATTR_PREDICATE) || ap) {
      if(nde == null)
        nde = getShownParent();
      if (nde != null) {
        nde.updateLocalAttribute(TreeNode.ATTR_SHOWN_CHILDREN);//TODO only on change
        parCh = true;
        //TODO move to VP
        //nde.getTreeDB().updateNodeChildren(nde);
      }
    }

    if (what.contains(TreeDB.INDEX_ATTRIBUTE)){
      Attribute attribute = what.getNew(TreeDB.INDEX_ATTRIBUTE);
      if (attribute != null) {
        Object nw = attribute.getValue();
        if (nw != null) this.index = (Index.create(nw.toString()));
        else
          this.index = Index.getUndefined();
      }
      else
        this.index = Index.getUndefined();
    }
    // //Links core
    // updateLink(s, oldAttribute,attribute);
    // if(s.equals(ATTR_REVLINKS)){
    // revlinks = null;
    // getRevertLinks();
    // }
    if (what.contains(ATTR_NAME)) {
      getTreeDB().updateRequirementName(this);
      // fire related to name update actions
      // TODO update needed?
      //updateTreeNodes(this, TreeNode.ATTR_NAME);
    }
    if(typeRelatedAttributeChangeReactionAfter(what, flag)) {
      if(nde == null)
        nde = getShownParent();
      cp = true;
    }
    // TODO move to db
    TreeNode templ = db.getTemplatesFolder();
    if (templ != null && getUUId().equals(templ.getUUId())) {
      if (what.contains(TreeDB.ACTIVE_TEMPLATES_ATTRIBUTE)) db.loadActiveTemplates();
    }
    if(cp && nde!=null)
      nde.commitAttributes();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (this == obj) return true;
    if (!(obj instanceof TreeNode)) return false;
    TreeNode node = (TreeNode) obj;
    if (!node.getSUUId().equals(getSUUId()))
      return false;
    if (!getId().equals(node.getId())) return false;
    if (getWorkParentUUID() == null ^ node.getWorkParentUUID() == null
        || getWorkParentUUID() != null
            && node.getWorkParentUUID() != null
            && (getWorkParentUUID()!=(node.getWorkParentUUID())
            || !getWorkParentUUID().equals(node.getWorkParentUUID()))) return false;
    if (attrs().hashCode() != (node.attrs().hashCode()))
      // TODO rewrite
      return false;
    // for (String key : attrs().getAttributeKeys()) {
    // Object v1 =
    // attrs().getAttributeValue(key),v2=node.attrs().getAttributeValue(key);
    // if (v1==null ^ v2==null || !v1.equals(v2))
    // return false;
    // }
    return true;
  }

  /**
   * Get children elements in array sorted by default sort mechanism
   *
   * @return array of children elements
   */
  public List<TreeNode> getSortedShownChildren() {
    return getSortedShownChildren(null, null, false);
  }

  /**
   * Get children elements of selected type in array sorted by default sort mechanism
   *
   * @param type required type of children
   * @return array of children elements of selected type
   */
  public List<TreeNode> getSortedShownChildren(String type) {
    return getSortedShownChildren(null, type, false);
  }

  public List<TreeNode> getSortedShownChildren(Comparator<TreeNode> c, String type) {
     return getSortedShownChildren(c, type, false);
  }

  /**
   * Get children elements of selected type in array sorted by order defined in given comparator
   *
   * @param c comparator that defines order of return elements
   * @param type required type of children
   * @param onlyloaded
   * @return array of children elements of selected type
   */
  public List<TreeNode> getSortedShownChildren(
      Comparator<TreeNode> c, String type, boolean notcheck) {
    List<Object> uuids = null;
    boolean needsSortingCheck = false;
    Attribute a = attrs().getAttribute(ATTR_SHOWN_CHILDREN);
    boolean notSorted = false;
    if(a == null) { 
      uuids = Sorting.formShownChilren(this, null, notcheck, false, false);//if virtual var is not inited - then use non cached list (without sorting)
      notSorted = true;
    }
    else {
      uuids = ((ListValue) a.getGValue()).getValues();
      needsSortingCheck = true;
    }
    if(uuids.size() == 0)
      return Collections.emptyList();
    List<TreeNode> ret = new LinkedList<>();
    TreeDB dba = db;
    if(dba.isVirtual())
      dba = Instantiator.getRootTree(db);//TODO needed?
    for(Object o : uuids) {
      TreeNode node = dba.getNode((UUID)o);
      if(node!=null && (type == null || type.equals(node.getType()))) 
        ret.add(node);
    }
    Comparator<TreeNode> comp = TreeNodeComparator.getComparator(dba);
    if(needsSortingCheck && (notSorted || c!=comp)) {
      if(c == null)
        c = comp;
      ret.sort(c);
    }
    return ret;
  }

  public List<TreeNode> getSortedWorkChildren() {
    return getSortedWorkChildren(null, null, false);
  }

  /**
   * Get children elements of selected type in array sorted by default sort mechanism
   *
   * @param type required type of children
   * @return array of children elements of selected type
   */
  public List<TreeNode> getSortedWorkChildren(String type) {
    return getSortedWorkChildren(null, type, false);
  }

  public List<TreeNode> getSortedWorkChildren(Comparator<TreeNode> c, String type, boolean notcheck) {
    return getSortedWorkChildren(c, type, notcheck, false, false);
  }

  public List<TreeNode> getSortedWorkChildren(
      Comparator<TreeNode> c, String type, boolean notcheck, boolean onlyloaded, boolean ignoreCPUP) {
    return Sorting.getSortedWorkChildren(this, c, type, notcheck, onlyloaded, ignoreCPUP);
  }

  public List<TreeNode> getShownChildren() {
    Attribute a = attrs().getActiveAttributes().get(ATTR_SHOWN_CHILDREN);
    List<Object> uuids = null;
    if(a == null) {
      uuids = Sorting.formShownChilren(this, null, false, false, false);//if virtual var is not inited - then use non cached list (without sorting)
    }else
      uuids = ((ListValue) a.getGValue()).getValues();
    List<TreeNode> nodes = Collections.emptyList();
    for(Object o : uuids){
      TreeNode node = getTreeDB().getNode((UUID)o);
      if(node!=null) {
        if(nodes.size() == 0)
          nodes = new ArrayList<>();
        nodes.add(node);
      }else
        RLogger.logDebug("Tries to load child " +o+" but it is not found "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
    }
    return nodes;
  }

  public List<TreeNode> getShownChildren(String type) {
    return getShownChildren(type, false);
  }

  public List<TreeNode> getShownChildren(String type, boolean notcheck) {
    List<TreeNode>nodes = new ArrayList<>();
    for(TreeNode node:getShownChildren())
      if(type == null || type.equals(node.getType()))
        nodes.add(node);
    return nodes;
  }
  
  public List<TreeNode> getWorkChildren() {
    return getWorkChildren(null, false);
  }

  public List<TreeNode> getWorkChildren(String type) {
    return getWorkChildren(type, false);
  }

  public List<TreeNode> getWorkChildren(String type, boolean notcheck) {
    return getWorkChildren(type, notcheck, false, false);
  }

  public List<TreeNode> getWorkChildren(String type, boolean notcheck, boolean onlyloaded, boolean ignoreCPUP) {
    return getWorkChildren(type,notcheck,onlyloaded,ignoreCPUP,true);
  }
  public List<TreeNode> getWorkChildren(String type, boolean notcheck, boolean onlyloaded, boolean ignoreCPUP, boolean logNPE) {
    ArrayList<TreeNode> result = new ArrayList<>();
    List<UUID> uids = getWorkChildrenUUIDs(onlyloaded||ignoreCPUP);
    //int num = sizeWorkChildren(onlyloaded);
    for (int i = 0; i < uids.size(); i++) {
      try{
        TreeNode ch = getNodeExc(uids.get(i),notcheck,onlyloaded);
        if (ch != null && (type == null || type.equals(ch.getType()))) {
          result.add(ch);
        }
      }catch(NodeNotFoundException nfe){
        if(logNPE)
          RLogger.logInfo(CoreUtils.getTrace(nfe));
      }
    }
    return result;
  }

  /**
   * Get path to resources folder related to current element
   *
   * @return path to resources folder
   */
  public URI getNodeResourcesPath() {
    try {
      return db.getStorage().getNodeResourcePath(uuid);
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }
  
  public URI getExternalNodeResourcesPath() {
    try {
      return db.getStorage().getExternalNodeResourcePath(db, uuid);
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  public File getNodeResourcesFile() {
    URI path = getNodeResourcesPath();
    if (path == null) return null;
    return new File(path);
  }

  /**
   * Get content of resource with given name corresponding to this element
   *
   * @param name name of resource to get
   * @return content of resource in form of stream to read
   */
  public InputStream getResourceContent(String name) {
    if (name == null) return null;
    if (!db.getStorage().isResourceExists(uuid, name)) return null;
    return db.getStorage().getResourceContents(uuid, name);
  }

  /**
   * Get java file object with given name
   *
   * @param name name of resource
   * @return java file
   */
  public File getResource(String name) {
    if (name == null) return null;
    if (!db.getStorage().isResourceExists(uuid, name)) return null;
    return db.getStorage().getResource(uuid, name);
  }

  /**
   * List all available resources for current element
   *
   * @return list of resources names
   */
  public Set<String> listResources() {
    return db.getStorage().listResources(uuid);
  }

  /**
   * Set content of resource with given name on current node
   *
   * @param name name of resource
   * @param in content of resource in form of stream
   */
  public void setResourceContent(String name, InputStream in, byte flag) {
    try {
      db.setResourceContent(this, name, in, null, flag);
    }
    finally {
      try {
        in.close();
      } catch (IOException e) {
        RLogger.logError(e);
      }
    }
  }

  /**
   * Set content of resource with given name on current node
   *
   * @param name name of resource
   * @param in content of resource in form of stream
   * @param data deprecated
   */
  public void setResourceContent(String name, InputStream in, Object data, byte flag) {
    try {
      db.setResourceContent(this, name, in, data, flag);
    }
    finally {
      try {
        in.close();
      } catch (IOException e) {
        RLogger.logError(e);
      }
    }
  }

  /**
   * Delete resource with given name from current element
   *
   * @param name name of resource to delete
   */
  public void deleteResource(String name, byte flag) {
    db.deleteResource(this, name, flag);
  }

  /**
   * Check if resource with given name exists on current element
   *
   * @param name name of resource to check
   * @return true if resource exists, false otherwise
   */
  public boolean isResourceExists(String name) {
    return db.isResourceExists(this, name);
  }

  protected Index index;

  public boolean isIndexUndefined() {
    return getIndex().isUndefined();
  }

  /**
   * Get index of element. Index is specific field that is used to define custom order of elements
   * in sorted list
   *
   * @return index of element
   */
  public Index getIndex() {
    if (index == null) {
      Attribute inda = attrs().getAttribute(TreeDB.INDEX_ATTRIBUTE);
      String indexStr = null;
      if (inda != null&&inda.getValue()!=null) indexStr = inda.getValue().toString();
      Attribute a = attrs().getAttribute(TreeDB.ORDER_ATTRIBUTE);
      if (a != null) {
        Object nw = a.getValue();
        if (nw instanceof String) {
          if (indexStr == null) indexStr = (String) nw; // old behaviour
        } else {
          if (indexStr == null) indexStr = ((Integer) nw).toString();
        }
      }
      if (indexStr != null) {
        index = Index.create(indexStr);
      } else {
        index = Index.getUndefined();
        ;
      }
    }
    return index;
  }

  /**
   * Set index of element. Index is specific field that is used to define custom order of elements
   * in sorted list
   *
   * @param index index of element
   */
  public void setIndex(Index index, boolean manual) {
    UUID par = getWorkParentUUID();
    if (par == null || par.equals(db.getRootNode().getUUId()))
      return; // ignore index udpate for roots
    //Attribute indexAttr = attrs().createAttribute(TreeDB.INDEX_ATTRIBUTE, index.toString());
    byte flag = Flag.HIDDEN;
    if(index.isUndefined())
      flag = (byte) (flag | Flag.VIRTUAL);
    attrs().setAttribute(new AttributeDefinition(uuid, TreeDB.INDEX_ATTRIBUTE, AttributeAvailability.LOCAL,
            new ConstValueProvider(AttributeType.STRING, index.toString()),flag));
        if(!manual)
          attrs().setAttribute(new AttributeDefinition(uuid, ATTR_INDEX_NOT_MANUAL, 
              new ConstValueProvider(AttributeType.BOOL, true),Flag.VIRTUAL));
        else
          attrs().removeAttribute(ATTR_INDEX_NOT_MANUAL);
    // TODO update needed?
    // updateTreeNodes(this, TreeDB.INDEX_ATTRIBUTE);
    if(!manual)
        getTreeDB().getCMM().getNewIndexesManipulator().addNewIndex(this);
      else{
        getTreeDB().getCMM().getNewIndexesManipulator().removeNewIndex(this.getTreeDB(), getUUId());
      }
  }

  /**
   * Get disposed flag for element. It is true if element is deleted.
   *
   * @return true if element is disposed, false otherwise
   */
  public boolean isDisposed() {
    return disposed||db.isDisposed();
  }

  public void setNOTDisposed() {
    disposed = false;
  }

  /** Set disposed flag for element to true. It is usually do after element deletion. */
  public void setDisposed() {
    disposed = true;
  }

  /**
   * If element have 'name' attribute then return it, else return ParentUserFriendlyName/Id Tends to
   * be unique in project by agreement
   *
   * @param inner if true then attribute will be created and updated further
   * @return path to element
   */
  public String getUserVisibleName(boolean inner) {
    if(inner)
    {
      return attrs().getAttribute(ATTR_USERVISIBLEID).getGValue();
    }
    else{
      Attribute a = attrs().getLocalAttributes().get(ATTR_USERVISIBLEID);
      if(a!=null)
        return a.getGValue();
      else
        return getVirtualAttributeDefinition(ATTR_USERVISIBLEID).getValue(attrs()).toString();//TODO tends not to be called
    }
//
//      StringBuilder sb = new StringBuilder();
//    getUserVisibleName(inner, true, sb);
//    return sb.toString();
  }

  public String getFullName(boolean inner) {
    return Requirement.getFullName(this, getTreeDB(), inner);
  }

  public String getFullName() {
    return getFullName(false);
  }

  public String getUserVisibleName() {
    return getUserVisibleName(false);
  }

//  public void getUserVisibleName(boolean inner, boolean fc, StringBuilder sb) {
//  
//  }

  /**
   * Method to update Requirement id, name and corresponding Location nodes
   *
   * @param req requirement to update
   * @param what name of attribute to be updated(or "_%id" for when identifier is updating)
   */
//  public void updateTreeNodes(TreeNode req, String what) {
//    updateTreeNodes(req, what, false);
//  }

  /**
   * Update Requirement id, name and corresponding locations
   *
   * @param req requirement to update
   * @param what name of attribute to be updated(or "_%id" for when identifier is updating)
   * @param internal true if call source is internal
   */
//  protected void updateTreeNodes(TreeNode req, String what, boolean internal) {
//    TreeNode[] reqs = getActiveWorkChildren();
//
//    // TODO update needed?
//    for (TreeNode node : reqs) {
//      if (!node.getUUId().equals(req.getUUId())) node.updateTreeNodes(req, what, internal);
//    }
//    // if(!internal)
//    // db.fireNodeChange(req, true);
//    // req.fireNodeAttributeChange(what);
//    // db.fireNodeGUIChange(req, true);
//    // req.fireGUINodeAttributeChange(what);
//  }

  private ReentrantReadWriteLock listenersLock = new ReentrantReadWriteLock();
  private LinkedBlockingQueue<INodeChangeListener> listenerList;
  private LinkedBlockingQueue<INodeChangeListener> guilistenerList;
  private ConcurrentLinkedQueue<IAttributeChangePropagator> propogators =
      new ConcurrentLinkedQueue<>();

  /**
   * Add Change Listener to be informed on changes of current node
   *
   * @param l change listener
   */
  public void addChangeListener(INodeChangeListener l) {
    WriteLock wl = listenersLock.writeLock();
    wl.lock();
    try{
      if (listenerList == null) listenerList = new LinkedBlockingQueue<>();
      if (!listenerList.contains(l)) listenerList.add(l);
    }finally{
      wl.unlock();
    }
  }

  /**
   * Remove change listener from current node
   *
   * @param l change listener to be removed
   */
  public void removeChangeListener(INodeChangeListener l) {
    WriteLock wl = listenersLock.writeLock();
    wl.lock();
    try{
      if (listenerList == null) return;
      if (listenerList.contains(l)) listenerList.remove(l);
    }
    finally{
      wl.unlock();
    }
  }
  
  
  public Queue<IAttributeChangePropagator> getAPropogators() {
    return propogators; 
  }
  
  public void addAPropogator(IAttributeChangePropagator a){
    if(a == null)
      RLogger.logError("Attempt to add null change prop!");
    else{
      propogators.add(a);
    }
  }
  public void removeAPropogator(IAttributeChangePropagator a){
    propogators.remove(a);
  }

  /**
   * Add change listener to be notified after set of actual changes
   *
   * @param l change listener to add
   */
  public void addGuiChangeListener(INodeChangeListener l) {
    WriteLock wl = listenersLock.writeLock();
    wl.lock();
    try{
      if (guilistenerList == null) guilistenerList = new LinkedBlockingQueue<>();
      if (!guilistenerList.contains(l)) guilistenerList.add(l);
    }finally{
      wl.unlock();
    }
  }

  /**
   * Remove change listener to be notified after set of actual changes
   *
   * @param l change listener to remove
   */
  public void removeGuiChangeListener(INodeChangeListener l) {
    ReadLock rl = listenersLock.readLock();
    rl.lock();
    try{
      if (guilistenerList == null) return;
      if (guilistenerList.contains(l)) guilistenerList.remove(l);
    }finally{
      rl.unlock();
    }
  }

  /** Fire event when node is deleted */
  protected void fireNodeDelete() {
    ReadLock rl = listenersLock.readLock();
    INodeChangeListener[] listeners = null;
    rl.lock();
    try{
      
      if (listenerList == null) return;
      listeners = listenerList.toArray(new INodeChangeListener[0]);
    }
    finally{
      rl.unlock();
    }
    int[] del = new int[listeners.length];
    int j = 0;
    for (int i = 0; i < listeners.length; i++) {
      INodeChangeListener k = listeners[i];
      try {
        k.deleted(this);
      } catch (Exception e) {
        e.printStackTrace();
        del[j++] = i;
      }
    }
//    WriteLock wl = listenersLock.writeLock();
//    wl.lock();
//    try{
//      for (int i = 0; i < j; i++) listenerList.remove(listeners[del[i]]);
//    }finally{
//      wl.unlock();
//    }
  }

  /**
   * Fire node change event when some attribute is changed
   *
   * @param changed attribute name
   */
  public void fireNodeAttributeChange(ChangesSet<Attribute> what) {
    ReadLock rl = listenersLock.readLock();
    INodeChangeListener[] listeners = null;
    int[] del = null;
    int j = 0;
    rl.lock();
    try{
      if (listenerList == null) return;
      listeners = listenerList.toArray(new INodeChangeListener[0]);
      del = new int[listeners.length];
    }
    finally{
      rl.unlock();
    }
    // for(String wh:what)
    for (int i = 0; i < listeners.length; i++) {
      INodeChangeListener k = listeners[i];
      try {
        k.attributesChange(this, what);
      } catch (Exception e) {
        // in the case of exception in
        // listener, delete it
        e.printStackTrace();
        del[j++] = i;
      }
    }
    
//    WriteLock wl = listenersLock.writeLock();
//    wl.lock();
//    try{
//      for (int i = 0; i < j; i++) listenerList.remove(listeners[del[i]]);
//    }finally{
//      wl.unlock();
//    }
  }

  /** Returns change listener that represents reaction on other nodes(used in linkto mechanics) */
  protected INodeChangeListener getChangeListener() {
    return null;
  }

  public boolean isNameDefined() {
    return attrs().getAttributeValue(ATTR_NAME) != null;
  }

  public String getNameOrId() {
    return attrs().getStringAttributeValue(ATTR_NAMEORID, "<ERROR_LOADING_NOId>");
  }


  public boolean setName(String name) {
    boolean changed = false;
    if (name == null || name.equals("")) {
      attrs().removeAttribute(ATTR_NAME);
    } else {
      name = name.replace((char) 160, ' ');
      AttributeDefinition newv = new AttributeDefinition(getUUId(), ATTR_NAME, AttributeAvailability.LOCAL,
          AttributeDefinition.detectValueProvider(
              TreeNode.ATTR_NAME, AttributeType.STRING,name, TempFolderFilesProvider.EMPTY),Flag.HIDDEN);
      //Attribute newv = attrs().createAttribute(ATTR_NAME, name);
      Attribute rem = attrs().setAttribute(newv);
      changed =
          rem == null
              || rem.getValue() == null
              || rem.getValue() != null && !rem.getValue().equals(newv.getRawValue().getValue());
    }
    return changed;
  }

  public String getName() {
    return attrs().getStringAttributeValue(ATTR_NAME);
  }

  /** Fire event when node is deleted */
  public void fireGUINodeDelete() {
    INodeChangeListener[] listeners = new INodeChangeListener[0];

    ReadLock rl = listenersLock.readLock();
    rl.lock();
    try{
      if (guilistenerList == null) return;
      listeners = guilistenerList.toArray(new INodeChangeListener[0]);
    }
    finally{
      rl.unlock();
    }
    int[] del = new int[listeners.length];
    int j = 0;
    for (int i = 0; i < listeners.length; i++) {
      INodeChangeListener k = listeners[i];
      try {
        k.deleted(this);
      } catch (Exception e) {
        e.printStackTrace();
        del[j++] = i;
      }
      // RLogger.logInfo(getType()+" "+getId()+" "+getName()+" p
      // "+(p==null?"null":p.getId()));
    }

//    WriteLock wl = listenersLock.writeLock();
//    wl.lock();
//    try{
//      for (int i = 0; i < j; i++) guilistenerList.remove(listeners[del[i]]);
//    }
//    finally{
//      wl.unlock();
//    }
  }

  /**
   * File event when attribute was changed
   *
   * @param what name of changed attribute
   */
  public void fireGUINodeAttributeChange(ChangesSet<Attribute> what) {
    ReadLock rl = listenersLock.readLock();
    
    INodeChangeListener[] listeners = new INodeChangeListener[0];
    rl.lock();
    try{
      if (guilistenerList == null) return;
        listeners = guilistenerList.toArray(new INodeChangeListener[0]);
    }finally{
      rl.unlock();
    }
    int[] del = new int[listeners.length];
    int j = 0;
    // for(String wh : what)
    for (int i = 0; i < listeners.length; i++) {
      INodeChangeListener k = listeners[i];
      try {
        k.attributesChange(this, what);
      } catch (Exception e) {
        // in the case of exception in
        // listener, delete it
        e.printStackTrace();
        del[j++] = i;
      }
    }
//    WriteLock wl = listenersLock.writeLock();
//    wl.lock();
//    try{
//      for (int i = 0; i < j; i++) guilistenerList.remove(listeners[del[i]]);
//    }finally{
//      wl.unlock();
//    }
  }

  /** Get iterator for children */
  public Iterator<TreeNode> workIterator() {
    //if (shownChildren == null) getShownChildrenUUIDs();
    return new TreeNodeChildIterator(false);
  }

  public Iterator<TreeNode> shownIterator() {
    //if (shownChildren == null) getShownChildrenUUIDs();
    return new TreeNodeChildIterator(true);
  }

  public void fireNodeAttributeChange(
     ChangesSet<Attribute> attributeName, AttributeAvailability availability) {
    fireNodeAttributeChange(attributeName);
    // if(availability != AttributeAvailability.LOCAL)
    // {
    // for(TreeNode child:getUnsortedChildren())
    // child.fireNodeAttributeChange(attributeName,
    // availability == AttributeAvailability.DIRECT_CHILDREN ?
    // AttributeAvailability.LOCAL : availability);
    //
    // }
  }

  public void fireGUINodeAttributeChange(
      ChangesSet<Attribute> attributeName, AttributeAvailability availability) {
    fireGUINodeAttributeChange(attributeName);
//    if (availability != AttributeAvailability.LOCAL) {
//      for (TreeNode child : getWorkChildren())
//        child.fireGUINodeAttributeChange(
//            attributeName,
//            availability == AttributeAvailability.DIRECT_CHILDREN
//                ? AttributeAvailability.LOCAL
//                : availability);
//    }
  }

  public List<UUID> getActiveWorkChildrenUUIDs() {
    ReadLock r = null;
    try{
      r = readWCLock();
      return new CopyOnWriteArrayList<>(workChildren);
    }finally{
      readWCUnlock(r);
    }
  }

  public List<UUID> getWorkChildrenUUIDs() {
    return getWorkChildrenUUIDs(true);
  }
  private boolean wcinited = false;
  public List<UUID> getWorkChildrenUUIDs(boolean ignoreCPUpdate) {
    ReadLock rl = readWCLock();
    try {
      return new CopyOnWriteArrayList<>(workChildren);
    }finally {
      readWCUnlock(rl);
    }
  }

  public int sizeWorkChildren() {
    return sizeWorkChildren(false);
  }

  public int sizeWorkChildren(boolean onlyloaded) {
    return getWorkChildrenUUIDs(true).size();
  }

  public TreeNode getWorkChild(int i) {
    return getWorkChild(i, false); // TODO maybenot to check
  }

  public TreeNode getWorkChild(int i, boolean notcheck) {
    return getWorkChild(i, notcheck, false);
  }
  protected TreeNode getNode(UUID child, boolean notcheck, boolean onlyloaded){
    try{
      return getNodeExc(child, notcheck, onlyloaded);
    } catch (NodeNotFoundException e) {
      e.printStackTrace();
    }
    return null;
  }
  TreeNode getNodeExc(UUID child, boolean notcheck, boolean onlyloaded) throws NodeNotFoundException{
    TreeNode node = null;
    if (onlyloaded) 
      node = db.getCachedNodeOrVirt(child);
    else
      node = db.getNode(child, TreeDB.DEFAULT_DEPTH, notcheck);
    if(node == null)
      return null;
    if (node.isVirtual() && !db.isVirtual()) {
      String uid = node.getVRoot();
      if (Instantiator.getExistVirtualTreeDB(db, uid) == null) Instantiator.getExistVirtualTreeDB(db, uid);
      if (onlyloaded)
        return Instantiator.getExistVirtualTreeDB(db, uid).getCachedNodeOrVirt(node.getUUId());
      else return Instantiator.getExistVirtualTreeDB(db, uid).getNode(node.getUUId());
    }
    return node;
  }
  public TreeNode getWorkChild(int i, boolean notcheck, boolean onlyloaded) {
    boolean wl = false;
    try{
      if(onlyloaded)
      {
        ReadLock r = null;
        try{
          r = readWCLock();
          UUID child = workChildren.get(i);
          return getNode(child, notcheck, onlyloaded);
        }finally{
          readWCUnlock(r);
        }
      }
      wl = writeWCLock();
      getWorkChildrenUUIDs(onlyloaded);
      UUID child = workChildren.get(i);
      return getNode(child, notcheck, onlyloaded);
    }
    finally{
      if(wl)
        writeWCUnlock();
    }
  }

  public boolean isLoaded() {
    return nodeLoaded;
  }
  public boolean isRestricted() {
    return false;
  }
  public static boolean isRoot(TreeDB db, UUID uid) {
    if (db.isVirtual()) { // in virt
      TreeInstancer ti = db.getVStorage();
      if (ti != null) return db.getVStorage().getRootNode().equals(uid);
      // TODO other trees
    } else return db.getRootNodeUUID().equals(uid);
    return false;
  }
  public boolean isRoot() {
    return isRoot(db, getUUId());
  }

  public TreeNode findSingleWorkChildPlusName(String rt) {
    List<TreeNode> ret = findWorkChildPlusName(rt);
    if (ret == null || ret.isEmpty()) return null;
    return ret.iterator().next();
  }

  public List<TreeNode> findWorkChildPlusName(String rt) {
    rt = rt.replace((char) 160, ' ');
    List<TreeNode> ret = new ArrayList<>();
    if (rt.startsWith("|:") && rt.endsWith(":|")) { // child num
      int num = Integer.parseInt(rt.substring(2, rt.length() - 2));
      if (num < sizeWorkChildren()) {
        ret.add(getSortedWorkChildren().get(num));
      }
      return ret;
    }
    List<UUID> uids = getWorkChildrenUUIDs();
    for (UUID uid : uids) {
      TreeNode child = db.getNode(uid);
      if(child!=null && child.getId() == null)
        RLogger.logInfo("Child "+uid+" in "+getUUId()+" loading problem");
      else
      if (child != null
          && (child.getId().toLowerCase().equals(rt.toLowerCase())
              || child.getName() != null && child.getName().equals(rt)
              || child.getSUUId().equals(rt)
              || child.getRawId().equals(rt))) {
        ret.add(child);
      }
    }
    return ret;
  }

  public List<TreeNode> findShownChildsPlusName(String rt) {
    rt = rt.replace((char) 160, ' ');
    List<TreeNode> ret = new ArrayList<>();
    if (rt.startsWith("|:") && rt.endsWith(":|")) { // child num
      int num = Integer.parseInt(rt.substring(2, rt.length() - 2));
      if (num < sizeShownChildren()) {
        ret.add(getSortedShownChildren().get(num));
      }
      return ret;
    }
    List<TreeNode> uids = getShownChildren();
    for (TreeNode child : uids) {
      //TreeNode child = db.getNode(uid);
      if (child != null && (child.getId()!=null && child.getId().equals(rt)
          || child.getName() != null && child.getName().equals(rt)
          || child.getSUUId().equals(rt))) {
        ret.add(child);
      }
    }
    return ret;
  }

  
  public AttributeStorage attrs() {
    if (attributes == null) attributes = AttributeStorage.loadOrCreate(this, db);
    return attributes;
  }

  public boolean canEditAttributesInTable() {
    return false;
  }

  /**
   * Save all attribute changes in storage. If it is not called after changes then all attribute
   * changes can be lost.
   */
  public void commitAttributes() {
    if(isDisposed())
      return;
//    if(!getTreeDB().inTransaction())
//    {
//      //System.out.println("change not in trans! "+CoreUtils.getTrace(Thread.currentThread().getStackTrace())); 
//    }
    AttributeStorage as = attrs();
    if (as.hasChanges()) {
      as.commitAttributes();
    }
  }

  public TreeDB getVirtualDB() {
    if (!isVirtual()) {
      String rmark = getVRoot();
      if (rmark != null) {
        return Instantiator.getExistVirtualTreeDB(db, rmark);
      } else return db; // Instantiator.getVirtualTreeDB(db,Instantiator.DEFAULT_INSTANCE_NAME);
    }
    return db;
  }
  public final AttributeDefinition getVirtualAttributeDefinition(String s) {
    if(s == null)
      return null;
    AttributeDefinition ad = vdefs.get(s);
    if(ad != null)
      return ad;
    String key = s;
    IGenericValueProvider p = ValueProviderFactory.getOne().getVirtualProvider(getTreeDB(), s, getUUId());
    if(p == null)
      return null;
    AttributeAvailability prop = AttributeAvailability.LOCAL;
    int flag = Flag.HIDDEN|Flag.VIRTUAL;
    if(p instanceof IAttributeFlagProvider)
      flag = ((IAttributeFlagProvider)p).getFlag(); 
    if(p instanceof IAttributePropogationProvider)
      prop = ((IAttributePropogationProvider)p).getScope();
    if(p instanceof TypeFilteredProvider)
      if(!((TypeFilteredProvider)p).testIsAvailiableOn(this))
        return null;
    if(p instanceof IValueProvider)
      ad = new AttributeDefinition(getUUId(), key, prop, (IValueProvider)p, flag);
    vdefs.put(s, ad);
    return ad;
  }
  protected boolean setChildren(List<UUID> storedChildren, boolean init) {
    boolean w = false;
    try{
      //attrs().updateAttribute(ATTR_CHILDREN_UUIDS);
      //TODO this is required when create is undoed, needs to be rewrited
      //commitAttributes();
      w = writeWCLock();
      boolean eqs = workChildren.equals(storedChildren);
      if (!eqs & !init) getVS().updateVersion(Type.STRUCT);
      if (storedChildren.contains(getUUId())) {
        RLogger.logInfo("WARN! rec prevented " + getQualifiedId(true));
        storedChildren.remove(getUUId());
      }
      this.workChildren = new ArrayList<>(storedChildren);
      if (!eqs & !init) {
        updateLocalAttribute(TreeNode.ATTR_CHILDREN_UUIDS);
        
        getVS().updateVersion(Type.STRUCT);
        //TODO return back clone update?
//        VirtualNode.updateClonesChildren(this);
      }
      return !eqs;
    }
    finally{
      if(w)
        writeWCUnlock();
    }
  }
  //
  // public void updateCPChildren() {
  // cPVersion = getCloneParent().getVersion().copy();
  // getTreeDB().updateNodeChildren(this);
  // }

  public VersionsStorage getVS() {
    return attrs().getVersionsStorage();
  }

  public String getVRoot() {
    if (vroot == null && attrs() != null) {
      Attribute parentRoot = attrs().getAttribute(TreeNode.ATTR_VIRT_STATUS);
      if (parentRoot != null && parentRoot.getValue() != null) {
        vroot = parentRoot.getValue().toString();
        return vroot;
      }
    }
    return vroot;
  }

  public void clearShownChildren() {
    if(attrs().getActiveAttributes().containsKey(ATTR_SHOWN_CHILDREN)) {
      attrs().removeAttribute(ATTR_SHOWN_CHILDREN);
      if(!isDisposed())
        commitAttributes();
    }
    //TODO remove this
//    //shownChildren = null;
//    getTreeDB().
//      fireNodeChildrenOrderChange(this,false);
  }

  public String getRawName() {
    Attribute a = attrs().getAttribute(ATTR_NAME);
    if(a==null || a.getDefinition().getRawValue() == null)
      return null;
    Object o = a.getDefinition().getRawValue().getValue();
    if(o == null)
      return null;
    return o.toString();
  }

  // public void removeClone(UUID uuid2) {
  // if(uuid2==null)
  // return;
  //// Attribute clns = attrs().getAttribute(TreeInstancer.ATTR_CLONES);
  //// if(clns!=null){
  //// List<Object> vals = clns.getGValue(attrs());
  //// if(vals!=null && vals.contains(uuid2.toString())){
  //// vals.remove(uuid2.toString());
  //// attrs().setAttribute(TreeInstancer.ATTR_CLONES,
  //// new ListValue(AttributeType.STRING, vals));
  //// }
  //// }
  // }

  public List<TreeNode> getActiveWorkChildren() {
    return getWorkChildren(null, false, true, true);
  }

  public boolean isHidden() {
    String s = "1";
    try {
      TreeInstancer ti = null;
      if (db.isVirtual()) ti = db.getVStorage();
      else {
        String vuid = getVRoot();
        if (vuid != null) ti = Instantiator.getExistVirtualTreeDB(db, vuid).getVStorage();
      }
      String pr = getPredicate();
      if (pr == null || pr.isEmpty()) s = "1";
      else if (ti == null) s = attrs().evaluatePredicateString(pr,AttributeStorage.formPredDeps(pr,attrs()));
      else s = ti.checkPredicate(getUUId(), pr);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return (s.startsWith("0") || s.equals("false"))
        && !getPredicate().equals("false");
  }

  public List<Pair<String, TreeNode>> getRelations(IObjectFilter<String> filter) {
    List<Pair<String, TreeNode>> list = new LinkedList<>();
    for (String s : attrs().getLocalAttributeKeys().toArray(new String[0])) // TODO all
    {
      if (filter == null || filter.filter(s)) {
        Attribute a = attrs().getAttribute(s);
        if (a != null && a.getType().equals(AttributeType.REFERENCE)) {
          List<TreeNode> linkeds = attrs().getLinkAttributeValue(a.getKey());
          if (linkeds != null)
            for (TreeNode linked : linkeds)
              list.add(new Pair<>(a.getKey(), linked));
        }
        if (a != null && (a.getListType() != null && a.getListType() == AttributeType.REFERENCE)) {
          try {
            List<TreeNode> nodes = attrs().getLinkAttributeValues(a.getKey());
            if (nodes != null)
              for (TreeNode node : nodes) list.add(new Pair<>(a.getKey(), node));
          } catch (Exception e) {
          }
        }
      }
    }
    return list;
  }

  public Set<TreeNode> getRelatedNodes(IObjectFilter<String> filter) {
    Set<TreeNode> nds = new HashSet<>();
    for (Pair<String, TreeNode> p : getRelations(filter)) {
      nds.add(p.getSecond());
    }
    return nds;
  }

  public void fireNodeResourceChange(String resName, Moment m) {
    ReadLock rl = listenersLock.readLock();
    rl.lock();
    if (listenerList != null){
      List<INodeChangeListener> list = new LinkedList<>(listenerList);
      rl.unlock();
      for (INodeChangeListener l : list) l.fireNodeResourceChange(this, resName, m);
    }else
      rl.unlock();
  }

  public void fireGUINodeResourceChange(String resName, Moment m) {
    ReadLock rl = listenersLock.readLock();
    rl.lock();
    if (guilistenerList != null){
      List<INodeChangeListener> list = new LinkedList<>(guilistenerList);
      rl.unlock();
      for (INodeChangeListener l : list)
        l.fireNodeResourceChange(this, resName, m);
    }else
      rl.unlock();
  }

//  public Version getNameVersion() {
//    Attribute a = attrs().getAttribute(ATTR_NAME);
//    if (a == null) return Version.getUnset();
//    return a.getVersion();
//  }

  public boolean hasResource(String string) {
    return db.getStorage().isResourceExists(uuid, string);
  }

  public static AttributeDefinition formIdAttribute(UUID uuid2, String nodeId) {
    //TODO change to proper
    return new AttributeDefinition(uuid2, ATTR_ID,AttributeAvailability.LOCAL,new ConstValueProvider(AttributeType.STRING, nodeId),
        Flag.HIDDEN);
  }


  public URI getNodeResourcesPath(String s) {
    URI rp = getNodeResourcesPath();
    if(rp!=null)
      return URI.create(rp.toString()+"/"+CoreUtils.URLEncode(s));
    return null;
  }
  public URI getExternalNodeResourcesPath(String s) {
    URI rp = getExternalNodeResourcesPath();
    if(rp!=null)
      return URI.create(rp.toString()+"/"+CoreUtils.URLEncode(s));
    return null;
  }

  public boolean isSameTree(TreeNode node) {
    if(node == null)
      return false;
    TreeDB db1 = getTreeDB(),
        db2 = node.getTreeDB();
    if(db1.isVirtual())
      db1 = Instantiator.getRootTree(db1);
    if(db2.isVirtual())
      db2 = Instantiator.getRootTree(db2);
    return db1==db2;
  }

  public boolean hasHeaderAttribute() {
    Attribute a = attrs().getAttribute("Вид");
    if(a!=null && a.getValue()!=null && a.getValue().toString().equals("Заголовок"))
      return true;
    return false;
  }
  public void setIgnoredName(String name) {
    setName(name);
    attrs().setAttribute(new AttributeDefinition(uuid, "ignoreName", 
        new ConstValueProvider(AttributeType.BOOL,true)));
  }
  public String getStoredNodeVersion(TreeDB dbfrom) {
    if(!AttributesVersioningRule.isRefVersEnabled(dbfrom))
      return null;
    Attribute a = attrs().getAttribute(AttributesVersioningRule.ATTR_NODE_VERSION);
    if(a!=null)
      return a.getGValue();
    return null;
  }
  public String getNodeVersion() {
    Attribute a = attrs().getAttribute(AttributesVersioningRule.ATTR_NODE_VERSION);
    if(a!=null)
      return a.getGValue();
    return AttributesVersioningRule.VALUE_NONE;
  }
  protected void fireLoaded(){
    nodeLoaded = true;
    synchronized (loadSync) {
      loadSync.notifyAll();
    }
  }
  protected void fireCreated(byte flag){
    fireLoaded();
    if(cloneParent!=null)
      getWorkChildren();//init children for clones
  }

  protected void applyLocalAttributes(byte flag, boolean withVirtuals) {
    ChangesSet<Attribute> changes = TreeDB.addAttrsRec(attrs(), withVirtuals, false, flag);

    Queue<IAttributeChangePropagator> prop = getAPropogators();
    boolean attrChanged = false;
    for(IAttributeChangePropagator pch : prop){
      if(pch!=null && pch.shouldBeApplied(this, changes, Moment.BEFORE)) {
        attrChanged = pch.apply(this, changes, Moment.BEFORE) || attrChanged;
      }
    }
    TreeDB db = getTreeDB();
    db.startChangeRow();
    try {
      reactBeforeAttributeChange(changes, flag);
      db.handleTreeNodeChanges(this, changes, Moment.BEFORE, flag);
  
      TreeDB.applyChanges(attrs(), changes);
      
      attrChanged = false;
      for(IAttributeChangePropagator pch : prop){
        if(pch.shouldBeApplied(this, changes, Moment.AFTER)) {
          attrChanged = pch.apply(this, changes, Moment.AFTER) || attrChanged;
        }
      }
      TreeDB.applyChanges(attrs(), changes);
      db.handleTreeNodeChanges(this, changes, Moment.AFTER, flag);
      attrs().clearChanges();
    }finally {
      db.stopChangeRow();
    }
  }
  @Override
  public int compareTo(TreeNode arg0) {
    return TreeNodeComparator.getComparator(getTreeDB()).compare(this, arg0);
  }
  public void waitLoaded() {
    waitLoaded(DEFAULT_TIMELIMIT);
  }
  public void waitLoaded(long i) {
    if(nodeLoaded)
      return;
    synchronized (loadSync) {
      try {
        loadSync.wait(i);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }      
    }
  }
  public void getChildrenRelatedVirtuals() {
    attrs().getLocalAttribute(TreeNode.ATTR_CHILDREN_UUIDS);//load current childs
    attrs().getLocalAttribute(TreeNode.ATTR_CHILDREN);//load current childs
    attrs().getLocalAttribute(TreeNode.ATTR_SHOWN_CHILDREN);//load current childs
  }
  @Override
  public TreeNode getNode() {
    return this;
  }
}
