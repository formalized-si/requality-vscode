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
 * limitations under the License.
 */
package com.unitesk.requality.core.model;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.regex.Pattern;

import com.unitesk.requality.core.IMultiPartDataProvider;
import com.unitesk.requality.core.MultiPart;
import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.api.IAttributeChangePropagator;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefToMerge;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeToMerge;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.AttributesRulesHolder;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.MergeParams;
import com.unitesk.requality.core.attribute.ReferenceOutdateMark;
import com.unitesk.requality.core.attribute.Version;
import com.unitesk.requality.core.attribute.VersionsStorage;
import com.unitesk.requality.core.attribute.VersionsStorage.Type;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.DescModeValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.events.TreeNodeChangeEvent.TreeNodeChangeEventType;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.exception.NullParentException;
import com.unitesk.requality.core.exception.OrderWasAlreadySetException;
import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.instancer.TreeInstancer;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.CommonRemoveLocation;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.LocationEventsHandler;
import com.unitesk.requality.core.operation.RemoveRequirementLocationCollection;
import com.unitesk.requality.core.operation.create.CreateNode;
import com.unitesk.requality.core.propagator.StructChangePropogator;
import com.unitesk.requality.core.secure.SecureStorage;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.core.storage.CacheInitializationStage;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.LinkResolver;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.storage.TermsManipulator;
import com.unitesk.requality.core.storage.TrackerCacheManipulatorsManager;
import com.unitesk.requality.core.storage.TreeEvent;
import com.unitesk.requality.core.storage.cache.CacheManipulatorsManager;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.storage.cache.ICacheStorageProvider;
import com.unitesk.requality.core.storage.cache.ProjSettsCacheMan;
import com.unitesk.requality.core.storage.cache.TreeCacheManipulatorsManager;
import com.unitesk.requality.core.storage.changelog.ChangeLogManager;
import com.unitesk.requality.core.storage.changelog.ChangeLogRecord;
import com.unitesk.requality.core.storage.formatversion.StoredFormatVersioning;
import com.unitesk.requality.core.transaction.ITransactionListener;
import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.NodeType;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.RestrictedFolder;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.ReportGenerationContext;
import com.unitesk.requality.report.ReportUtils;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.LocationHelper;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class TreeDB implements ICacheStorageProvider, IMultiPartDataProvider{
  public static final int DEFAULT_DEPTH = 4;
  public static final String ORDER_ATTRIBUTE = "_order";
  public static final String INDEX_ATTRIBUTE = "_index";
  public static final String TEST_GENERATORS_FILTERS = "_testGenFilters";
  public static final String DEFAULT_REFERENCE_NAME = "_defRefName";
  public static final String ACTIVE_TEMPLATES_ATTRIBUTE = "_active_templates";
  public static final String EMPTY_TEMPLATE = "&empty";
  private static final String PREFERENCE_FNMODE = "_com.unitesk.requality.full_name_mode";

  public Boolean simpleLabelMode = null;
  private MultiPart mpSettings = null;
  public String cachedLocation = null;
  private Map<String, Object> metaObjects = new HashMap<>();
  private int loa = 0;
  private String operationId = null;
  private String localName = null;
  private String projectName = null;

  private Map<UUID, IMark[]> chprob = new HashMap<>();
  private Map<UUID, ConcurrentLinkedQueue<IMark>> chwarns = new HashMap<>();
  TreeNode root;
  private NodeCache cache = new NodeCache();
  private ChangeLogManager clm;
  private List<String> testgenfilters;
  private ITreeStorage storage;
  private IUUIDGenerator uuidGenerator;
  private TreeLogic tlogic;
  private IResTracker tracker;
  private boolean isDisposed = false;
  public static char DATA_SEPARATOR = ((char) 0xA6);
  
  private Map<String, NodeType> defaultTypes = new HashMap<>();

  public void registerMetaObject(String name, Object obj) {
    synchronized (metaObjects) {
      metaObjects.put(name, obj);
    }
  }
  
  public <T> T getMetaObject(String name){
    synchronized (metaObjects) {
      return (T)metaObjects.get(name);
    }
  }
  private boolean isVirtual;

  private ConcurrentLinkedQueue<TreeNodeChangeEvent> guichanges =
      new ConcurrentLinkedQueue<>();
  private int activeChangesRowsCount = 0;

  private Map<String, String> activeTemplates = null;

  protected CheckerContainer container = null;
  
  private ArrayList<ITransactionListener> transactionListeners =
      new ArrayList<>();
  private static List<String> typeFilter = new ArrayList<>();
  static final Pattern new_linkto_pattern =
      Pattern.compile("\\[linkto: ([^\\]]*)\\]", Pattern.MULTILINE & Pattern.DOTALL);
  static final Pattern linkto_pattern =
      Pattern.compile(
          "\\[<span id=\"([a-zA-Z0-9-]+)\">.{0,10}</span>linkto: ([^\\]]*)\\]",
          Pattern.MULTILINE & Pattern.DOTALL);
  private static final Comparator<String> systemKeyPriority = new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        if(o1.isEmpty())
          return -1;
        if(o2.isEmpty())
          return 1;
        boolean s1 = o1.startsWith("_");
        boolean s2 = o2.startsWith("_");
        if(s1)
          return s2?0:-1;
        return s2?1:0;
      }
  };
  
  public Map<UUID, Set<String>> attrRootRevCache = new HashMap<>();
  public Map<String, Set<UUID>> attrRootCache = new HashMap<>();

  private Map<String, TreeNode> btg = null;
  
  private AttributesRulesHolder vrh;
  
  public boolean getLabelIsSimple(){
    if(simpleLabelMode == null)
      simpleLabelMode = root.attrs().getBooleanAttributeValue(
          DescModeValueProvider.LABEL_IS_SIMPLE_PARAM,true);
    return simpleLabelMode;
  }
 
  public List<ChangeLogRecord> getChangeLog(TreeNode node) {
    if(clm == null)
      clm = new ChangeLogManager(this);
    return clm.getChangeLog(node);
  }
  
  public AttributesRulesHolder getAttributeRules(){
    if(vrh == null)
      vrh = new AttributesRulesHolder(this);
    return vrh;
  }
  
  public void clearTagsCache() {
    btg = null;
  }

  public TreeNode getByTags(TreeNode node, String link) {
    if (btg == null) {
      btg = new HashMap<>();
      TreeNode rt = node.getTreeDB().getNode(Requirement.getTypeRootQId());
      DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(rt);
      Iterator<TreeNode> it = dftw.iterator();
      while (it.hasNext()) {
        TreeNode nd = it.next();
        Attribute a = nd.attrs().getAttribute("_tags");
        if (a != null && !nd.isDisposed()) {
          List<Object> list = ((ListValue)a.getGValue()).getValues();
          for (Object o : list) btg.put(o.toString(), nd);
        }
      }
    }
    return btg != null ? btg.get(link) : null;
  }

  public String[] getNodesByUFName(String s, List<String> filter) {
    TreeNode[] nodes = getLinkResolver().getNodesByUFName(this, s, filter);
    String[] arr = new String[nodes.length];
    for (int i = 0; i < nodes.length; i++) arr[i] = nodes[i].getSUUId();
    return arr;
  }
  private String checkerLock = "checkerLock";
  public CheckerContainer getCheckerContainer() {
    return getCheckerContainer(revision);
  }
  public CheckerContainer getCheckerContainer(RevisionInfo ri) {
    synchronized (checkerLock) {
      if (container == null) 
        container = Global.getCheckersContainer(this, revision == null ? ri : revision);  
    }
    return container;
  }

  public int getFullNameMode() {
    if (root == null) return 0;
    Attribute a = root.attrs().getAttribute(PREFERENCE_FNMODE);
    if(a == null)
      return 0;
    Object fname = a.getValue();
    if(fname !=null)
      try {
        String val = fname.toString();
        if(val.toLowerCase().equals("true"))//old
          return 1;
        return a != null && a.getValue()!=null ? Integer.parseInt(val) : 0;
      }
      catch(NumberFormatException e) {
        return 0;//non byte var
      }
  return 0;
  }
 
  public void setFullNameMode(int useUVName) {
    if (root == null) return;
    root.attrs().setConstAttribute(PREFERENCE_FNMODE, useUVName, Flag.HIDDEN);
    root.commitAttributes();
  }

  public void loadActiveTemplates() {
    activeTemplates = new HashMap<>();
    if (getTemplatesFolder() == null) return;
    Attribute a = getTemplatesFolder().attrs().getAttribute(ACTIVE_TEMPLATES_ATTRIBUTE);
    if (a != null) {
      ListValue info = (ListValue) a.getValue();
      for (Object o : info.getValues()) {
        String s = o.toString();
        String type = s.substring(0, s.indexOf("|"));
        String id = s.substring(s.indexOf("|") + 1);
        if (id.isEmpty()) activeTemplates.put(type, null);
        else {
          TreeNode node = getTemplate(id);
          if (node != null) activeTemplates.put(type, id);
          else activeTemplates.put(type, null);
        }
      }
    }
  }

  public Map<String, String> getActiveTemplates() {
    synchronized (this) {
      if (activeTemplates == null) loadActiveTemplates();
    }
    return activeTemplates;
  }

  public void saveActiveTemplates() {
    List<Object> infos = new ArrayList<>();
    for (String s : getActiveTemplates().keySet()) {
      String name = getActiveTemplates().get(s);
      if (name != null) {
        TreeNode tmpl = getTemplate(name);
        if (tmpl != null) infos.add(s + "|" + tmpl.getId());
        else infos.add(s + "|");
      } else infos.add(s + "|");
    }
    TreeNode tf = getOrCreateTemplatesFolder();
    tf.attrs().setConstAttribute(ACTIVE_TEMPLATES_ATTRIBUTE, new ListValue(AttributeType.STRING, infos),
        Flag.HIDDEN);
    
    getOrCreateTemplatesFolder().commitAttributes();
  }
  private TreeNode templf;
  public TreeNode getTemplatesFolder() {
    try {
      if(templf!=null)
        return templf;
      templf = getRootNode().findWorkChild("Templates");
    } catch (NodeNotFoundException re) {
    }
    return templf;
  }

  INodeChangeListener templch =
      new INodeChangeListener() {
        @Override
        public void moved(TreeNode node, TreeNode oldParent) {}

        @Override
        public void deleted(TreeNode source) {}

        @Override
        public void attributeChange(TreeNode source, String what, Attribute old) {
          if (what.equals(ACTIVE_TEMPLATES_ATTRIBUTE)) {
            loadActiveTemplates();
          }
        }

        @Override
        public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
          if (what.contains(ACTIVE_TEMPLATES_ATTRIBUTE))
            attributeChange(source, ACTIVE_TEMPLATES_ATTRIBUTE, what.getOld(ACTIVE_TEMPLATES_ATTRIBUTE));
        }

        @Override
        public void fireNodeResourceChange(
            TreeNode treeNode, String resName, Moment m) {
          // TODO Auto-generated method stub

        }
      };
  public CacheStorage getCacheStorage() {
    return getStorage().getCacheStorage();
  }

  public ProjSettsCacheMan getPSCacheMan(){
    return getCacheStorage().getPSCM();
  }
  
  public TreeNode getOrCreateTemplatesFolder() {
    TreeNode node = getTemplatesFolder();
    if (node == null) 
      node = CreateNode.call(root, RestrictedFolder.TYPE_NAME, "Templates");
    node.addChangeListener(templch);
    return node;
  }

  public TreeNode createTemplate(String type) {
    TreeNode fol = getOrCreateTemplatesFolder();
    String id = RequalityCLI.getNextId(fol, type, "Template_");
    return CreateNode.call(fol,type,id);
  }

  public boolean extractTemplate(TreeNode node, String name) {
    TreeNode fold = getOrCreateTemplatesFolder();
    if (getTemplate(name) != null) return false;
    Map<String, AttributeDefinition> map = new HashMap<>();
    UUID newuid = UUID.randomUUID();
    Map<String, Attribute> omap = node.attrs().getMap();
    for (String s : new HashSet<>(omap.keySet())) {
        Attribute a = omap.get(s);
        if(!a.isVirtual() && !a.isReadOnly(node.getTreeDB()))
          map.put(s, a.getDefinition().copy(newuid));
      // map.put(a.getKey(), list);
      // }
      // else
      // map.put(a.getKey(), a);
    }
    int num = 0;
    String retname = name;
    while (fold.findWorkChild(retname) != null) {
      retname = name + "_" + num;
      num++;
    }
    NodeDesc nd =
        new NodeDesc(
            this,
            newuid,
            null,
            fold.getUUId(),
            fold.getUUId(),
            retname,
            map,
            new ArrayList<UUID>(),
            true);
    nd.createNode(this);
    return true;
  }

  public List<TreeNode> getTemplates() {
    List<TreeNode> templates = new ArrayList<>();
    TreeNode tfolder = getTemplatesFolder();
    if (tfolder == null) return Collections.emptyList();
    for (TreeNode templ : tfolder.getWorkChildren()) // TODO sort
    templates.add(templ);
    return templates;
  }

  public TreeNode getTemplate(String name) {
    if (name == null || name.equals("&empty")) return null;
    if (getTemplatesFolder() == null) return null;
    return getTemplatesFolder().findWorkChild(name);
  }

  public void setActiveTemplate(String type, String templ) {
    getOrCreateTemplatesFolder();
    getActiveTemplates().put(type, templ);
    saveActiveTemplates();
  }

  public TreeNode getActiveTemplate(String type) {
    if (!getActiveTemplates().containsKey(type)) return null;
    return getTemplate(getActiveTemplates().get(type));
  }

  private ConcurrentLinkedQueue<TreeNodeChangeEvent> getGuiChanges() {
    if (isVirtual) { // Test here
      TreeDB db = Instantiator.getBaseTree(this);
      if (db != null && db != this) return db.getGuiChanges();
    }
    return guichanges; // TODO check
  }

  @SuppressWarnings("unchecked")
  public List<String> getTestGeneratorsFilters() {
    if (testgenfilters == null)
      testgenfilters =
          (List<String>)
              getRootNode()
                  .attrs()
                  .getListAttributeValue(TEST_GENERATORS_FILTERS, AttributeType.STRING);
    return testgenfilters;
  }

  /**
   * Returns Type of element with given uid if it exists.
   *
   * @param uid uuid of element
   * @return type of element or null
   */
  public String getNodeType(UUID uid) {
    if (isLoaded(uid)) return getNode(uid).getType();
    String tp = storage.getNodeRawType(uid);
    if (tp == null) {
      AttributeStorage as = AttributeStorage.getCachedStorage(this, uid);
      if (as != null){
        Object res = AttributeStorage.getAttributeValue(this, as.getActiveAttributes(),uid,TreeNode.ATTR_TYPE);
        if(res != null)
          return res.toString();
      }
    }
    return tp;
  }
  private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
  private ReadLock rl = rwl.readLock();
  private WriteLock wl = rwl.writeLock();
  //private String cacheStart = null;
  /** Combine all future events in a row to be handled concurrently. */
  public void startChangeRow() {
  //  if(CoreUtils.DEBUG_LEVEL>1)
 //   RLogger.logInfo("Start change row on "+ResManager.getManager().getName(TreeDB.this)+" "+activeChangesRowsCount+" "+CoreUtils.getTrace(Arrays.copyOf(Thread.currentThread().getStackTrace(),14)));
    wl.lock();
    activeChangesRowsCount++;
    wl.unlock();
  }

  /**
   * Returns 'virtuality' status of catalog. Catalog is virtual if it is linked to virtual storage.
   *
   * @return true if catalog is virtual
   */
  public boolean isVirtual() {
    return isVirtual;
  }


  /** Stop event row started with startChangeRow() method */
  @SuppressWarnings("unchecked")
  public void stopChangeRow() {
    wl.lock();
    boolean wlock = true;
    try{
      activeChangesRowsCount--;
      if (activeChangesRowsCount <= 0){ 
        try{
          clearRemPool();
        }catch(Exception e){
          RLogger.logError(e);
        }
      }
      if(CoreUtils.DEBUG_LEVEL>7)
        RLogger.logInfo("Stop change row for "+ResManager.getManager().getVisibleName(this)); 
      if (activeChangesRowsCount == 0) {
        if(CoreUtils.DEBUG_LEVEL>7)
          RLogger.logInfo("Row is 0, actions for "+ResManager.getManager().getVisibleName(this));
        // TODO needed?
        HashMap<UUID, Integer> lastchange = new HashMap<>(); 
        HashMap<Integer, Integer> changeStart = new HashMap<>(); 
        LinkedList<Integer>changeStarts = new LinkedList<>();
        // lastmove = new HashMap<UUID, Integer>();
        HashSet<UUID> deleted = new HashSet<>(), created = new HashSet<>();
        HashMap<UUID, ChangesSet<Attribute>> changeParams = new HashMap<>();
        int i = 0;
        TreeNodeChangeEvent lastfocus = null;
        List<TreeNodeChangeEvent> handled = new LinkedList<>();
        while (!getGuiChanges().isEmpty()) {
          TreeNodeChangeEvent change = getGuiChanges().poll();
          handled.add(change);
          if (change == null || change.getType() == null) continue;
          if (change.getType() == TreeNodeChangeEventType.DELETE) {
            NodeDesc nd = change.getNodeDescTarget();
            if (nd != null) {
              deleted.add(nd.getUUId());
            } else {
              TreeNode node = change.getTreeNodeTarget();
              if(node!=null)
                deleted.add(node.getUUId());
            }
          } else if (change.getType() == TreeNodeChangeEventType.CHANGE) {
            Moment m = change.getMoment();
            if(m == Moment.AFTER) {
              if(!changeStarts.isEmpty()) {//incomplete change sets
                Integer start = changeStarts.removeLast();
                changeStart.put(i, start);
              }else
                RLogger.logDebug("Incomplete change set, requires rebuilding of code");
            }else
              changeStarts.addLast(i);
            TreeNode node = change.getTreeNodeTarget();
            {
              UUID uid = node.getUUId();
              lastchange.put(uid, i);
              ChangesSet<Attribute>na = (ChangesSet<Attribute>) change.getArguments()[0];
              if(na!=null){
                if (!changeParams.containsKey(node.getUUId()))
                  changeParams.put(node.getUUId(), na);
                else
                  try {
                    changeParams.put(node.getUUId(), 
                        changeParams.get(node.getUUId()).merge((ChangesSet<Attribute>) change.getArguments()[0]));
                  } catch (OrderWasAlreadySetException e) {
                    RLogger.logError(e);
                  }
              }
            }
          } else if (change.getType() == TreeNodeChangeEventType.MOVE) {
            change.getTreeNodeTarget();
          }
          if (change.getType() == TreeNodeChangeEventType.CREATE) {
            TreeNode node = change.getTreeNodeTarget();
            if(node!=null){
              created.add(node.getUUId());
            }
          }
          i++;
        }
//        for (UUID uid : deleted) {
//          lastchange.remove(uid);
//          // lastmove.remove(uid);
//          created.remove(uid);
//        }
//        for (UUID uid : created) {
//          lastchange.remove(uid);
//          // lastmove.remove(uid);
//        }
        if(wlock){
          wlock = false;
          wl.unlock();
        }
        i = 0;
        Set<Integer> events = new HashSet<>();
        events.addAll(lastchange.values());
        Set<Integer> eventsStarts = new HashSet<>();
        for(Integer ea:events)
          eventsStarts.add(changeStart.get(ea));
        // events.addAll(lastmove.values());
        List<TreeNodeChangeEvent> retevents = new LinkedList<>();
        if(CoreUtils.DEBUG_LEVEL>7)
          RLogger.logInfo("Before change set  on "+ResManager.getManager().getVisibleName(TreeDB.this));
          
        while (!handled.isEmpty()) {
  
          TreeNodeChangeEvent change = handled.remove(0);
          try {
            if (change != null){
              byte flag = change.getFlag();
              switch (change.getType()) {
                case CHANGE:
                  {
                    TreeNode node = change.getTreeNodeTarget();
                    try {
                      if (events.contains(i)) {
                        ChangesSet<Attribute> params = changeParams.get(node.getUUId());
                        Object[] args = change.getArguments();
                        args[0] = params;
                        retevents.add(
                            new TreeNodeChangeEvent(TreeNodeChangeEventType.CHANGE, Moment.AFTER, node, flag, args));
                        change.getTreeNodeTarget().fireGUINodeAttributeChange(params);
                      }
                      if(eventsStarts.contains(i)) {
                          ChangesSet<Attribute> params = changeParams.get(node.getUUId());
                          Object[] args = change.getArguments();
                          args[0] = params;
                          retevents.add(
                              new TreeNodeChangeEvent(TreeNodeChangeEventType.CHANGE, Moment.BEFORE, node, flag, args));
                          //change.getTreeNodeTarget().fireGUINodeAttributeChange(params);
                      }
                    } catch (NullPointerException p) {
                      p.printStackTrace();
                    }
                  }
                  break;
                case CACHE_CHANGE:
                  {
                    TreeNode node = change.getTreeNodeTarget();
                    if (node != null) {
                      retevents.add(change);
                    }
                  }
                  break;
                case CREATE:
                  {
                    TreeNode node = change.getTreeNodeTarget();
                    if (node != null) {
                      retevents.add(change);
                    }
                    // change.getTreeNodeTarget().fireNodeAttributeChange(
                    // change.getArguments().length>1?(String)change.getArguments()[1]:TreeNode.ATTR_UNDEFINED);
                  }
                  break;
                case DELETE:
                  {
                    retevents.add(change);
                    TreeNode targ = change.getTreeNodeTarget();
                    if (targ != null) targ.fireGUINodeDelete();
                  }
                  break;
                case MOVE:
                  {
                    // if(events.contains(i)){
                    retevents.add(change);
                    // }
                    // change.getTreeNodeTarget().fireNodeAttributeChange(
                    // change.getArguments().length>2?(String)change.getArguments()[2]:TreeNode.ATTR_UNDEFINED);
                  }
                  break;
                case SETFOCUS:
                  {
                    TreeNode node = change.getTreeNodeTarget();
                    if (!node.isDisposed()) lastfocus = change;
                  }
                  break;
                default:
                 retevents.add(change);
              }
            }
          } catch (Exception e) {
            RLogger.logInfo("error during event ex");
            RLogger.logError(e);
          }
          i++;
        }

        LocationEventsHandler.callUpdate(retevents);
        if(CoreUtils.DEBUG_LEVEL>7)
          RLogger.logInfo("After change set  on "+ResManager.getManager().getVisibleName(TreeDB.this));
        if (lastfocus != null) retevents.add(lastfocus);
        List<ITreeChangeListener> list = getGUIListeners();
        if (retevents.size() > 0 && list!=null)
          for (ITreeChangeListener l : list) {
            l.changed(retevents);
          }
      }
    }
    finally{
      if(wlock){
        wlock = false;
        wl.unlock();
      }
    }
  }

  private void clearRemPool() {
//    while (!rempool.isEmpty()) {
//      TreeNode node = rempool.pop();
//      if (!node.isDisposed()) node.delete();
//    }
  }

  /**
   * Get specific object delegated for different checks like check on parent-child relations and
   * element identifiers check.
   *
   * @return TreeLogic object
   */
  public TreeLogic getTreeLogic() {
    if (tlogic == null) tlogic = new TreeLogic();
    return tlogic;
  }

  private TreeDB(IResTracker tracker, int loa2) {
    this.loa = loa2;
    this.tracker = tracker;
    
  }
  private static TreeState[] HALTED_STATES = new TreeState[] {TreeState.HALTED, TreeState.RESTART};

  //private IStateChangeHandler loadedHook = null;
  private IStateChangeHandler haltedHook = null;
  //private IStateChangeHandler restartHook = null;

  //TODO test place where it is used to be after clearing of old tree
  public void addTreeStateHooks(String lname, String pname) {
    
    IResTracker tracker = getTracker();
    TreeStateManager tsm = tracker.getTreeStateManager();
    if(lname!=null) {
      //halted hook
      boolean hookAdded = false;
      synchronized(this) {
        if(haltedHook!=null) {
          RLogger.logDebug("Attempt to add halted hook on tree where it was set "+lname);
        }else {
          hookAdded = true;
          haltedHook = new AAllTreeStateChangeHandler() {
            boolean hookRemoved = false;
            @Override
            public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
              TreeState oldState, TreeState state) {
              //TODO test it on reopen
              if(!hookRemoved) {
                if(haltedHook == this) {
                  haltedHook = null;
                  hookRemoved = true;
                }
                tsm.removeStateHook(lname, HALTED_STATES, this);
              }
              getCheckerContainer().stopOldThread();
            }
          };
        }
      }
      if(hookAdded)
        tsm.addStateHook(lname, HALTED_STATES, haltedHook);
      hookAdded = false;
    }
  }
    
  public void initRootTypes(){
    NodeType dbroot = new NodeType("DBRoot", TreeDB.this);
    dbroot.setClass(com.unitesk.requality.core.model.TreeNode.class);
    defaultTypes.put("DBRoot", dbroot);
    initNodeAttributes(dbroot,dbroot.attrs().getChanged().getNewMap(), true, OFlag.NONE);
    
    NodeType nodetype = new NodeType("NodeType", TreeDB.this);
    nodetype.setClass(com.unitesk.requality.nodetype.NodeType.class);
    defaultTypes.put("NodeType", nodetype);
    initNodeAttributes(nodetype,nodetype.attrs().getChanged().getNewMap(), true, OFlag.NONE);
    
    NodeType vnodetype = new NodeType("VirtualNode", TreeDB.this);
    vnodetype.setClass(com.unitesk.requality.nodetype.VirtualNode.class);
    defaultTypes.put(VirtualNode.TYPE_NAME, vnodetype);
    initNodeAttributes(vnodetype,vnodetype.attrs().getChanged().getNewMap(), true, OFlag.NONE);
    
    NodeType req = new NodeType("Requirement", TreeDB.this);
    req.setClass(com.unitesk.requality.nodetype.Requirement.class);
    defaultTypes.put(Requirement.TYPE_NAME, req);
    initNodeAttributes(req,req.attrs().getChanged().getNewMap(), true, OFlag.NONE);
    
    NodeType node = new NodeType("Node", TreeDB.this);
    node.setClass(com.unitesk.requality.core.model.TreeNode.class);
    defaultTypes.put("Node", node);
    initNodeAttributes(node,node.attrs().getChanged().getNewMap(), true, OFlag.NONE);

  }

  public TreeNode createChildNode(UUID pUUId, String id, String type) {
    return createChildNode(pUUId, id, type, EMPTY_TEMPLATE);
  }

  /**
   * Method to create child element for element with pUUId identifier.
   *
   * @param pUUId parent UUID for element
   * @param id identifier of element, needs to comply with TreeLogic.isChildIdValidExc
   * @param type type of element, @see {@link TreeNode.getType}
   * @param templ
   * @return created Element or null
   */
  public TreeNode createChildNode(UUID pUUId, String id, String type, String templ) {
    UUID uuid = uuidGenerator.nextUUId();
    TreeNode ret = createChildNode(uuid, pUUId, id, type, templ, false);
    updateNodeChildren(ret.getWorkParent(), true);//TODO work?
    return ret;
  }

  public TreeNode createChildNode(
      UUID uuid, UUID pUUId, String id, String type, boolean ignoreTemplate) {
    return createChildNode(uuid, pUUId, id, type, EMPTY_TEMPLATE, ignoreTemplate);
  }

  /**
   * Method to create child element for element with pUUId identifier.
   *
   * @param uuid UUID of element
   * @param pUUId parent UUID for created element
   * @param id identifier of element, needs to comply with TreeLogic.isChildIdValidExc
   * @param type type of element, @see {@link TreeNode.getType}
   * @return created Element or null
   */
  public TreeNode createChildNode(
      UUID uuid, UUID pUUId, String id, String type, String templ, boolean ignoreTemplate) {
    TreeNode node = getNode(pUUId);
    if(node == null || node.isDisposed())
      throw new IllegalArgumentException(
          "Child with id: \"" + id + "\" cannot be created in disposed or absent node " + getNode(pUUId).getQualifiedId());
    if (node.findWorkChild(id) != null) {
      throw new IllegalArgumentException(
          "Child with id: \"" + id + "\" exists in " + getNode(pUUId).getQualifiedId());
    }
    if (type == null) RLogger.logInfo("null type on node "+uuid);
    storage.createNode(pUUId, uuid, type, id, ignoreTemplate?null:templ, OFlag.NONE);
    return getNode(uuid);
  }

  public TreeNode nodeCreated(UUID uuid, String type, byte flag) {
    TreeNode node = getNode(uuid), rnode = getLinkResolver().getRefTarget();
    Map<String,AttributeDefinition> map = 
        getAttributeRules().getRequiredAttrRule().getMap().get(type);
    boolean changed = false;
    if(map!=null) {
      File resFolder = node.getNodeResourcesFile();
      for(Entry<String, AttributeDefinition> ev:map.entrySet()){
        AttributeDefinition ad = ev.getValue();
        TempFolderFilesProvider.getOne(ad.getType(), resFolder, true);
        node.attrs().setAttribute(new AttributeDefinition(node.getUUId(), 
            ev.getKey(), AttributeDefinition.detectValueProvider(ev.getKey(), 
                ad.getType(), ad.getFlags(), ad.getRawValue().getValue(),
                TempFolderFilesProvider.getOne(ad.getType(), resFolder, true))));
        changed = true;
      }
    }
    if (node != null && Requirement.TYPE_NAME.equals(node.getType()) && rnode != null) {
      changed = true;
      
//      Map<UUID, String> vers = null;
//      Map<String, UUID> valToUID = null;
      
      //if(getAttributeRules().get)
//        valToUID = new HashMap<>();
//        vers = new HashMap<>();
//        vers.put(rnode.getUUId(), rnode.getNodeVersion());
//        valToUID.put(getDefReferenceName(), rnode.getUUId());
      
      node.attrs().setAttribute(new AttributeDefinition(node.getUUId(), 
          getDefReferenceName(), new ReferenceValueProvider(AttributeType.REFERENCE, 
              rnode.getUserVisibleName(true)),AttributeDefinition.detectFlags(getDefReferenceName())));

      //node.attrs().getLinkAttributeValue(getDefReferenceName());
      if (getLinkResolver().getRefTargetState(rnode) == 1) {
        getLinkResolver().setRefTarget(null);
        Map<String, Attribute> old = new HashMap<>();
        old.put(getDefReferenceName(), rnode.attrs().getAttribute(getDefReferenceName()));
        ChangesSet<Attribute> cs = new ChangesSet<>();
        try {
          cs.addSomeChange(getDefReferenceName(),old.get(getDefReferenceName()),rnode.attrs().getAttribute(getDefReferenceName()));
        } catch (OrderWasAlreadySetException e) {
          RLogger.logError(e);
        }
        if(!OFlag.isNoGuiUpdate(flag)) {
            fireNodeGUIChange(
                rnode,
                cs,
                flag, Moment.BEFORE);//TODO move upper
            fireNodeGUIChange(
                rnode,
                cs,
                flag, Moment.AFTER);
        }
      }
      if (getLinkResolver().getRefTargetState(rnode) == 0) rnode = null;
    }
    if(node!=null){
      if (changed) node.commitAttributes();
      if(!OFlag.isNoUpdate(flag))
        node.fireCreated(flag);
    }
    return node;
  }

  public TreeNode createChildNode(NodeDescSet desc, String template, byte flag) {
    if (getNode(desc.root.getWorkParentUUID()).findWorkChild(desc.root.getId()) != null) {
      throw new IllegalArgumentException(
          "Child with id: \"" + desc.root.getId() + "\" exists in " + getNode(desc.root.getWorkParentUUID()).getQualifiedId());
    }
    String type = null;
    try {
      type = desc.root.getAttributes().get(TreeNode.ATTR_TYPE).getRawValue().getValue().toString();
    } catch (Exception e) {
    }
    if (type == null) 
      RLogger.logInfo("null type for node "+desc.root.getUUId());
    storage.createNode(desc, template, OFlag.NONE);
    TreeNode node = getNode(desc.root.getUUId()), rnode = getLinkResolver().getRefTarget();
    //TODO rewrite .getLinkResolver() related part
    if (getLinkResolver().getRefTargetState(rnode) == 0) rnode = null;
    if (node != null && Requirement.TYPE_NAME.equals(node.getType()) && rnode != null) {
   
      node.attrs().setAttribute(new AttributeDefinition(
          node.getUUId(),getDefReferenceName(),
          new ReferenceValueProvider(AttributeType.REFERENCE, rnode.getUserVisibleName(true)),
          AttributeDefinition.detectFlags(getDefReferenceName())));
      node.attrs().getLinkAttributeValue(getDefReferenceName());
      if (getLinkResolver().getRefTargetState(rnode) == 1) {
        getLinkResolver().setRefTarget(null);
        Map<String, Attribute> old = new HashMap<>();
        old.put(getDefReferenceName(), rnode.attrs().getAttribute(getDefReferenceName()));
        ChangesSet<Attribute> cs = new ChangesSet<>();
        try {
          cs.addSomeChange(getDefReferenceName(),null,old.get(getDefReferenceName()));
        } catch (OrderWasAlreadySetException e) {
          RLogger.logError(e);
        }
        if(!OFlag.isNoGuiUpdate(flag)) {
            fireNodeGUIChange(
                rnode,
                cs,
                flag, Moment.BEFORE);//TODO move upper
            fireNodeGUIChange(
                rnode,
                cs,
                flag, Moment.AFTER);
        }
      }
      node.commitAttributes();
    }
      
    return node;
  }

  /**
   * Recursively remove element and all its descendants. Child elements are removed first.
   *
   * @param node element to remove
   */
  private Set<UUID> alreadyRemoved = new ConcurrentSkipListSet<UUID>();
  public static NodeDescSet initNodeDescSet(ITreeStorage storage, UUID uuid) {
    return NodeDescSet.fromNodeDesc(initNodeDesc(storage, uuid), true);
  }
  public static NodeDesc initNodeDesc(ITreeStorage storage, UUID uuid) {
    Map<String, AttributeDefinition> attrs = storage.getAttributes(uuid);
    if(attrs == null || attrs.isEmpty()) {
      attrs = storage.getAttributes(uuid);
      RLogger.logDebug("Attributes set is empty for node to be loaded "+uuid);
      return null;
    }
    NodeDesc nd = new NodeDesc(uuid, storage.getParent(uuid), 
        storage.getNodeId(uuid), new HashMap<>(attrs), false);
    initResources(storage, nd, uuid);
    LinkedList<Pair<UUID, NodeDesc>> toh = new LinkedList<>();
    Collection<UUID> chi = storage.getChildren(uuid);
    if(chi!=null){
      for(UUID uid:chi) 
        toh.add(new Pair<>(uid, nd));
      nd.setChildrenUUIDs(chi);
      while(!toh.isEmpty()) {
        Pair<UUID, NodeDesc>p = toh.removeFirst();
        NodeDesc parent = p.getSecond();
        UUID uid = p.getFirst();
        if(storage.canLoad(uid)){
          NodeDesc nd2 = new NodeDesc(uid, parent.getUUId(), 
                  storage.getNodeId(uid), 
                  new HashMap<>(storage.getAttributes(uid)),
                  false);
          initResources(storage, nd2, uid);
          parent.setChildrenDesc(uid, nd2);
          Collection<UUID> chi2 = storage.getChildren(uid);
          if(chi2!=null){
            nd2.setChildrenUUIDs(chi2);
            for(UUID uid2:chi2)
              toh.add(new Pair<>(uid2, nd2));            
          }
        }      
      }
    }
    return nd;
  }
  private static void initResources(ITreeStorage storage, NodeDesc nd2, UUID uid) {
    Collection<String> res = storage.listResources(uid);
    if(res!=null && res.size()>0) {
      for(String s: res)
        nd2.putResource(s, storage.getResource(uid, s));
    }
  }

  public void removeNode(TreeNode node, byte flag) {
    removeNode(node, flag, false);
  }
  public void removeNode(TreeNode node, byte flag, boolean ignoreLocUpdate) {
    //TODO move corresponding removes(loc+) here
    if(node.isDisposed())
      return;
    if(flag==OFlag.NONE && VirtualNode.toBeDeleted(node, node.getWorkParent())){
      node.getWorkParent().delete();
      return;
    }
    if (node.isVirtual() && !TreeStateManager.getOne(node.getTreeDB().getTracker()).isVirtual(this)) {
      TreeDB db = Instantiator.getExistVirtualTreeDB(this, node.getVRoot());
      if (db != this) {
        db.removeNode(node, flag);
        return;
      }
    }
    UUID uuid = node.getUUId();
    //Without resources and with temporary removed set
    NodeDescSet nd = initNodeDescSet(storage, uuid);
    RemoveRequirementLocationCollection r = new RemoveRequirementLocationCollection();

    Set<TreeNode> pars = new HashSet<>();
    for(NodeDesc tnd:nd.allNodesRemove) {
      Collection<UUID> uids = Instantiator.getClones(tnd.getUUId(), this);
      for(UUID cluid:uids) {
        TreeNode clone = getCachedNodeOrVirt(cluid);
        if(clone!=null && !pars.contains(clone.getWorkParent()))
          pars.add(clone.getWorkParent());
      }
    }
    nd.allNodesRemove
      .forEach(one -> {
        if(one!=null) {
          alreadyRemoved.add(one.getUUId());
          if(!ignoreLocUpdate) {
            String tp = one.getType();
            if(!OFlag.isNoUpdate(flag))
              switch (tp) {
                case TextNode.TYPE_NAME:
                case Requirement.TYPE_NAME:
                {
                  if(one.getAttributes().containsKey(Requirement.ATTR_LOCATIONS))
                    CommonRemoveLocation.getOne().
                      reactOnRequirementRemove(TreeDB.this, one, flag, false, r);
                }
                break;
                case Location.TYPE_NAME:
                {
                  CommonRemoveLocation.getOne().
                      reactOnLocationRemove(TreeDB.this, one.getUUId(), flag, false, r);
                }
                break;
              }
          }
        }
      }
    );
    startChangeRow();
    CacheStorage cspa = getCPCacheStorage(this);//TODO needed?
    CacheTransactionDescription tr2 = cspa==null?null:cspa.startCacheTransaction("remove nodes starting from "+node.getUserVisibleName(), 2);
    try{
      alreadyRemoved.addAll(r.getLocationsToRemoveUUIDs());
      for(TreeNode req:r.getRequiremnetsToUpdate()) {
        UUID uid = req.getUUId();
        if(!alreadyRemoved.contains(uid)) {//if node was not in removed set
          List<UUID> lnodes = r.getRemovedLocs(uid);
          Requirement reqNode = (Requirement) req;
          boolean updated = false;
          if(reqNode.removeLocationNodeDescs(lnodes))
            updated = true;
          if(updated) {
            reqNode.commitAttributes();
            reqNode.updateLocations(reqNode);
          }
        }
      }
      //form set of changed nodes here and define in which order send events
      storage.remove(nd, flag);//TODO change?

      for(UUID luid: r.getLocationsToUpdate()) {
        TreeNode lnode = getNode(luid);
        if(lnode!=null)
          ((Location)lnode).resetLabel();
      }
      for(TreeNode lnode: r.getLocationsToRemove())
        removeNode(lnode, flag, true);
      for(TreeNode clone:pars) {
        VirtualNode.updateChildrenOfClone(clone);
      }
    }
    catch(Exception e){
      RLogger.logError(e);
    }
    finally{
      if(cspa!=null && tr2!=null && tr2.error == null)
        cspa.commit(tr2);
      stopChangeRow();
    }
  }
  
  private void clonesReact(UUID uuid, TreeNode oldParent, TreeNode newParent) {
    TreeDB db = TreeDB.this;
    if(isVirtual)
      db = Instantiator.getRootTree(db);
    LinkedList<TreeNode> pars1 = new LinkedList<>(),
        pars2 = new LinkedList<>();
    TreeNode nde = newParent;
    while (nde != null) {
      pars1.addFirst(nde);
      nde = nde.getWorkParent();
    }
    nde = oldParent;
    while (nde != null) {
      pars2.addFirst(nde);
      nde = nde.getWorkParent();
    }
    Iterator<TreeNode> it1 = pars1.iterator(), it2 = pars2.iterator();
    StringBuilder sb = new StringBuilder();
    TreeNode node1 = null, node2 = null;
    while (it1.hasNext() && it2.hasNext()) {
      node1 = it1.next();
      node2 = it2.next();
      if (!node1.getUUId().equals(node2.getUUId())) break;
    }
    if (node1 != null && node2 != null && node1.getUUId().equals(node2.getUUId())) {
      node1 = node2 = null;
      if (it1.hasNext()) node1 = it1.next();
      if (it2.hasNext()) node2 = it2.next();
    }
    while (node2 != null) {
      if (sb.length() > 0) sb.append("/");
      sb.append("..");
      node2 = null;
      if (it2.hasNext()) node2 = it2.next();
    }
    while (node1 != null) {
      if (sb.length() > 0) sb.append("/");
      sb.append(node1.getUUId());
      node1 = null;
      if (it1.hasNext()) node1 = it1.next();
    }
    String sbs = sb.toString();
    // TODO ugly version
    Set<UUID> clones = new HashSet<>(Instantiator.getClones(uuid, db));
    TreeNode node = db.getNode(uuid);
    if (clones.size() > 0) {
      for (UUID uid : clones) {
        try {
          TreeNode clone = db.getNode(uid);
          if (clone == null) continue;
          TreeNode posClone = clone.getWorkParent();
          // on place
          if (sbs.length() > 0) {
            String[] correction = sbs.split("/");
            for (int i = 0; i < correction.length; i++) {
              boolean parVNode = posClone.getType().equals(VirtualNode.TYPE_NAME);
              String action = correction[i];
              if(parVNode && !((VirtualNode)posClone).isBase()){
                posClone = null;
                break;//if vnode is not base then all moves are ignored
              }
              if (action.equals("..")) {
                if (parVNode) {
                  posClone = null; // cannot get upper chan
                  // vnode
                  break;
                }
                posClone = posClone.getWorkParent();
                if (!posClone.isVirtual()
                    && !VirtualNode.TYPE_NAME.equals(posClone.getType())) { // TODO
                  // change
                  // for
                  // proper
                  posClone = null;
                  break;
                }
              } else {
                posClone =
                    Instantiator.getChildOrClone(db, posClone, UUID.fromString(action));
                if (posClone == null) break;
              }
            }
          }
          if (posClone == null)
            try {
              clone.delete();
            } catch (Exception e) {
              RLogger.logError("Implement clone delete "+CoreUtils.getShortTrace());
  //                deleted(clone.getUUId(), Moment.BEFORE, true);
  //                deleted(clone.getUUId(), Moment.AFTER, true);
            }
          else {
            // if(posClone.getWorkParent().getType().equals(VirtualNode.TYPE_NAME))
            // npclones.remove(posClone.getWorkParent().getUUId());
            // npclones.remove(posClone.getUUId());
            if (!clone.getUUId().equals(uuid)) {
              int cnt = 0;
              for (UUID ndeu : posClone.getActiveWorkChildrenUUIDs()) {
                Object cpo =
                    AttributeStorage.getAttributeDefValue(
                        db, db.getAttributes(ndeu), ndeu, TreeNode.ATTR_CLONE_PARENT);
                if (cpo != null
                    && cpo.toString().equals(clone.getCloneParentUUId().toString())) cnt++;
              }
              if (cnt <= 1) { // if only one{
                try {
                  clone.attrs().setConstAttribute(TreeInstancer.ATTR_CLONE_ID, node.getId(),Flag.HIDDEN);
                  clone.setId(node.getId(), true);
                  clone.commitAttributes();
                } catch (Exception e) {
                  RLogger.logError(e);
                }
              }
            }
            if (!posClone.getUUId().equals(clone.getWorkParent().getUUId()))
              clone.setParent(posClone);
            // if(!posClone.getParent(true).getType().equals(VirtualNode.TYPE_NAME)){
  
            // }
            // if(!clone.getId().equals(node.getId()))
            // clone.setId(node.getId());
          }
        } catch (Exception e) {
          // RLogger.logError(e);
        }
      }
      // deleted(node.toNodeDesc(false));
    }
  }

  /**
   * Move element from one parent to another
   *
   * @param node node to move
   * @param newParent new parent of element
   */
  public void moveNode(TreeNode node, TreeNode newParent) {
    TreeNode oldPar = node.getWorkParent();
    CacheTransactionDescription ret = null;
    Lock wl = storage.writeLock(null);
    CacheStorage cs = storage.getCacheStorage();
    try{
      ret = cs.startCacheTransaction("Move node",CacheStorage.COMMIT_SINGLE);
      storage.moveNodeTo(node.getUUId(), newParent.getUUId(), OFlag.NONE);
    }catch(Exception r){
      if(ret != null && ret.error == null)
      {
        ret.error("already rolled back");
        cs.rollback(ret);
      }
      throw r;
    }
    finally{
      if(ret != null && ret.error == null)
        cs.commit(ret);
      wl.unlock();
    }
    clonesReact(node.getUUId(), oldPar, newParent);
    newParent.getVS().updateVersion(Type.STRUCT);
    updateNodeChildren(node.getWorkParent(), true);//TODO work?
  }
 

  /**
   * Set identifier of node to id
   *
   * @param node element to set new identifier
   * @param id new identifier
   */
  void setId(TreeNode node, String id) {
    if (!node.getId().equals(id)) {
      storage.changeNodeId(node.getUUId(), id,  OFlag.NONE);
      // if(isVirtual){
      // Attribute ca = node.attrs().getLocalAttribute(TreeInstancer.ATTR_CLONE_ID);
      // if(ca.getOrigin().equals(node.getUUId())){
      // if(!ca.getValue().toString().equals(node.getCloneParent().getId())){
      // node.attrs().setAttribute(TreeInstancer.ATTR_CLONE_ID, id);
      // node.attrs().saveAttributes();
      // }
      // }
      // }
      //
    }
  }
  public String getRootNodeSUUID() {
    return root!=null?root.getSUUId():storage!=null&&storage.getRootNode()!=null?storage.getRootNode().toString():null;
    //TODO mb storage to string
  }
  public UUID getRootNodeUUID() {
    return root!=null?root.getUUId():storage!=null?storage.getRootNode():null;
  }
  
  public TreeNode getCachedRootNode() {
    return root;
  }
  /** @return root node of TreeDB */
  public TreeNode getRootNode() {
    if (root == null) {
      UUID rootUUID;
      if ((rootUUID = storage.getRootNode()) == null) { // root doesn't
        // exists
        return null;
      }
      try{
        root = getNode(rootUUID, DEFAULT_DEPTH, false);
      }catch(NodeNotFoundException nne){
        RLogger.logInfo(CoreUtils.getTrace(nne));
        return null;
      }
    }
    return getCachedRootNode();
  }

  /**
   * Method to update cached requirement name.
   *
   * @param node requirement to update name
   */
  public void updateRequirementName(TreeNode node) {
    // if(!(node instanceof Requirement))
    // return;
    // Requirement r = (Requirement)node;
    // if(getLinkResolver().hasRevNameCache(node.getUUId()))
    getLinkResolver().updateNameInCache(node.getUUId(), node.getName());
  }
  public TreeNode getNode(UUID uuid){
    try{
      return getNode(uuid, DEFAULT_DEPTH, false);
    }
    catch(NodeNotFoundException nnfe){
      RLogger.logDebug(CoreUtils.getTrace(nnfe));
      return null;
    }
  }
  
  public boolean isNodeExist(UUID uid){
    TreeNode node = getCachedNode(uid);
    if(node!=null)
      return !node.isDisposed();
    return storage.canLoad(uid);
  }
  
  public TreeNode getNodeExc(UUID uuid) throws NodeNotFoundException, NullParentException{
    return getNode(uuid, DEFAULT_DEPTH, false);
  }

  public TreeNode getCachedNodeOrVirt(UUID uuid) {
    return getCachedNodeOrVirt(uuid, DEFAULT_DEPTH);
  }

  public TreeNode getCachedNodeOrVirt(UUID uuid, int deep) {
    TreeNode node = cache.get(uuid);
    if (node != null) {
      return node;
    }
    if (isVirtual && deep > 0)
      return Instantiator.getRootTree(this).getCachedNodeOrVirt(uuid, deep - 1);
    if (!isVirtual && deep > 0) { // TODO another dbs
      for (TreeDB db : Instantiator.getVirtualTreeDBs(this))
        // TreeDB db =
        // Instantiator.getVirtualTreeDB(this,Instantiator.DEFAULT_INSTANCE_NAME);
        if (db != null && db != this) {
          TreeNode nde = db.getCachedNodeOrVirt(uuid, deep - 1);
          if (nde != null) return nde;
        }
    }
    return null;
  }

  public TreeNode getCachedNode(UUID uuid) {
    TreeNode node = cache.get(uuid);
    if (node != null) {
      return node;
    }
    return null;
  }

  // Condition cacheCond = cacheLock.newCondition();
  @SuppressWarnings("unchecked")
  /**
   * Method to get element by UUID
   *
   * @param uuid element identifier
   * @return element with given uuid
   */
  public TreeNode getNode(UUID uuid, int depth, boolean notcheck) throws NodeNotFoundException, NullParentException{
    return getNode(uuid, depth, notcheck, true);  
  }
  private ConcurrentMap<UUID, Object> nodeLoadSync = new ConcurrentHashMap<>();
  public TreeNode getNode(UUID uuid, int depth, boolean notcheck, boolean ignoreRemoved) throws NodeNotFoundException, NullParentException{
    if (uuid == null) return null;
    TreeNode node = getCachedNodeOrVirt(uuid, DEFAULT_DEPTH);
    if (node != null) {
      return node;
    }
    if (root != null && root.getUUId().equals(uuid)) return root;
    synchronized (nodeLoadSync) {
    if(!nodeLoadSync.containsKey(uuid))
        nodeLoadSync.put(uuid, uuid);
  }
    if(ignoreRemoved&&alreadyRemoved.contains(uuid))
      return null;
    boolean isClone = false, canLoad = false;
    Map<String, AttributeDefinition> attrs = null;
    boolean isroot = false;
    String type = null;
    if (CoreUtils.DEBUG_LEVEL > 7) {
      RLogger.logDebug("Loading " + uuid);
    }
    long l = System.currentTimeMillis();
    TreeDB db = this;
    TreeNode cached = getCachedNodeOrVirt(uuid);
    if(cached!=null)
      return cached;
    synchronized (nodeLoadSync.get(uuid)) {
      Object[] tl = testLoad(uuid, depth);
      canLoad = tl != null;
      if (tl != null) {//to avoid warn
        attrs = (Map<String, AttributeDefinition>) tl[0];
        canLoad = (boolean) tl[1];
        type = (String) tl[2];
        isroot = (boolean) tl[3];
        isClone = (boolean) tl[4];
      }
      if (CoreUtils.DEBUG_LEVEL > 7) {
        RLogger.logDebug(uuid + " Determine params " + (System.currentTimeMillis() - l));
        l = System.currentTimeMillis();
      }
      if (!isroot) isroot = db.getRootNodeUUID().equals(uuid);
      // if(isVirtual && isClone && first)
      // return VirtualNode.getBaseTree(this).getNode(uuid,false);//single
      // deep into
      if (!canLoad) {
        db = null;
        if (depth > 0) {
          if (depth > 0 && !isVirtual()) {
            if (uuid.equals(storage.getRootNode())) {
              RLogger.logInfo("Root node damaged! on " + getVisibleName());
              return null; // root damaged!
            }
            if (attrs == null
                || attrs.get(TreeNode.ATTR_VIRT_STATUS) == null
                || attrs.get(TreeNode.ATTR_VIRT_STATUS) == null)
              db = this; // Instantiator.getVirtualTreeDB(this,Instantiator.DEFAULT_INSTANCE_NAME);
            else
              db =
                  Instantiator.getExistVirtualTreeDB(
                      this,
                      attrs.get(TreeNode.ATTR_VIRT_STATUS).getRawValue().getValue().toString());
  
            // if(db!=null && db!=this)
            // return db.getNode(uuid,depth-1,notcheck);
          } else if (isVirtual()) {
            db = Instantiator.getBaseTree(TreeDB.this);
            // if(db!=null && db!=this)
            // return db.getNode(uuid,depth-1,notcheck);
          }
        }
      }
      if (db != null && db != this) {
        tl = db.testLoad(uuid, depth);
        if (tl == null) canLoad = false;
        else {
          attrs = (Map<String, AttributeDefinition>) tl[0];
          canLoad = (boolean) tl[1];
          type = (String) tl[2];
          isroot = (boolean) tl[3];
          isClone = (boolean) tl[4];
          if (!isroot) isroot = db.getRootNodeUUID().equals(uuid);
        }
      }
      if (!canLoad) 
        canLoad = isClone || isroot;
      if (CoreUtils.DEBUG_LEVEL > 7) {
        RLogger.logDebug(uuid + " Get proper tree " + (System.currentTimeMillis() - l));
        l = System.currentTimeMillis();
      }
      if (db == null && canLoad) db = this;
      if (db != null && canLoad) {
        if (attrs == null) throw new RuntimeException("Null attributes!");
        node = cache.getNode(this, uuid);
        if (node != null) return node;
        // code below was written to deal with the problem of
        // loops in some case and to add change listeners
        // for linked Nodes.
        try {
          return cache.loadNode(this, uuid,type,attrs, notcheck);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          RLogger.logError(e);
        }
      }
      throw new NodeNotFoundException(uuid, getSemiLocalName());
    }
  }

  private TreeNode getNode(NodeDesc nd, boolean notcheck) throws NodeNotFoundException{
    if (nd == null) return null;
    TreeNode node = getCachedNodeOrVirt(nd.getUUId(), DEFAULT_DEPTH);
    if (node != null) {
      return node;
    }
    UUID uuid = nd.getUUId();
    if (root != null && root.getUUId().equals(uuid)) return root;
    boolean isroot = false;
    String type = nd.getType();
    if (CoreUtils.DEBUG_LEVEL > 7) {
      RLogger.logDebug("Loading " + uuid);
    }
    long l = System.currentTimeMillis();
    TreeDB db = this;
    synchronized (nodeLoadSync) {
    if(!nodeLoadSync.containsKey(uuid))
        nodeLoadSync.put(uuid, uuid);
  }
    synchronized (nodeLoadSync.get(uuid)) {
      //Object[] tl = testLoad(storage, uuid, depth);
//      canLoad = tl != null;
//      if (tl != null) {//to avoid warn
//        attrs = (Map<String, AttributeDefinition>) tl[0];
//        canLoad = (boolean) tl[1];
//        type = (String) tl[2];
//        isroot = (boolean) tl[3];
//        isClone = (boolean) tl[4];
//      }
      if (CoreUtils.DEBUG_LEVEL > 7) {
        RLogger.logDebug(uuid + " Determine params " + (System.currentTimeMillis() - l));
        l = System.currentTimeMillis();
      }
      if (!isroot) isroot = db.getRootNodeUUID().equals(uuid);

      if (db != null) {
        if (nd.getAttributes() == null) throw new RuntimeException("Null attributes!");
        node = cache.getNode(this, uuid);
        if (node != null) return node;
        // code below was written to deal with the problem of
        // loops in some case and to add change listeners
        // for linked Nodes.
        try {
          return cache.loadNode(this, uuid,type,new HashMap<>(nd.getAttributes()), notcheck);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          RLogger.logError(e);
        }
      }
      throw new NodeNotFoundException(uuid, getSemiLocalName());
    }
  }

  private void print(StackTraceElement[] trace) {
        System.out.println(this);
        for (StackTraceElement traceElement : trace)
            System.out.println("\tat " + traceElement);
  }

  private Object[] testLoad(UUID uuid, int depth) {
    if (storage.canLoad(uuid)) {

      boolean isClone = false, canLoad = false;
      Map<String, AttributeDefinition> attrs = null;
      boolean isroot = false;
      String type = null;
      attrs = storage.getAttributes(uuid);
      Object ta = AttributeStorage.getAttributeDefValue(this, attrs, uuid, TreeNode.ATTR_TYPE);
      if (ta != null) type = ta.toString();
      if (type != null) {
        if (attrs != null) {
          canLoad = true;
          boolean virt = false;
          if (canLoad
              && attrs.containsKey(TreeNode.ATTR_VIRT_STATUS)
              && attrs.get(TreeNode.ATTR_VIRT_STATUS) != null) {
            Object val = attrs.get(TreeNode.ATTR_VIRT_STATUS).getRawValue().getValue();
            // TODO trans storage
            if (val != null) { // can be loaded only by virt. tree
              canLoad = isVirtual && getVStorage().getRootNode().toString().equals(val);
              isroot = uuid.toString().equals(val);
              virt = true;
              if (depth == 0) canLoad = true;
            }
          }
          if (!isroot && Instantiator.isDefVRoot(this, uuid, attrs)) {
            isroot = true;
            virt = true; // hack
          }
          if (!virt && isVirtual()) canLoad = false;
          // if(isroot&&!isVirtual()&&forceload)
          // canLoad=true;
          if (attrs.containsKey(TreeNode.ATTR_CLONE_PARENT)
              || attrs.containsKey("_cloneparent")) isClone = true;
        }
      }
      return new Object[] {attrs, canLoad, type, isroot, isClone};
    }
    return null;
  }

  /**
   * Method to get element by "QualifiedId" or "User-visible name" or "Full name"
   *
   * @param qId path to element
   * @return element if exists, null otherwise, can throw Exception
   */
  public TreeNode getNode(String qId) {
    return getNode(qId, Path.QID | Path.UV | Path.UUID);
  }

  /**
   * Method to get element by "QualifiedId" or "User-visible name" or "Full name". If rawpath
   * parameter is true then search ignores hide mechanics.
   *
   * @param qId path to element
   * @param rawPath if true, ignore hide mechanisms
   * @return element if exists, null otherwise, can throw Exception
   */
  public TreeNode getNode(String qId, int mode) {
    try{
      return getNodeExc(qId,mode);
    }
    catch(NodeNotFoundException nnfe){
      
    }
    return null;
  }
  public TreeNode getNodeExc(String qId, int mode) {
    if(qId == null)
      return null;
    int pos = qId.indexOf("::");
    if(pos>-1)
    {
      String dbname = qId.substring(0, pos);
      qId = qId.substring(pos+2);
      TreeDB fdb = tracker.getTreeDB(this,null, dbname, false, getMonitor());//TODO IDE monitor?
      if(fdb == null)
        return null;
      return fdb.getNode(qId);
    }
    List<TreeNode> res = getLinkResolver().findNodesByPathWithDisposed(null, null, this, qId, mode);
    if (res == null || res.size() == 0) return null;
    return res.iterator().next();
  }
  /**
   * Save attributes of element with uuid as attributes
 * @param flag 
   * @param uuid identifier of element
   */
  public void commitAttributes(TreeNode node, ChangesSet<Attribute> changes, boolean save, boolean propagateStruct, byte flag) {
    // do not need to save if nothing changes
    if (changes.isEmpty())
      return;
    startChangeRow();
    try {
      //TreeDB.addAttrsRec(node, changes.getAll(),changes, map);
      //Map<String, AttributeDefinition> old = storage.getAttributes(node.getUUId());
  //    Map<String, Version> va = storage.getVersions(uuid);
      Queue<IAttributeChangePropagator> prop = node.getAPropogators();
      boolean attrChanged = false;
      for(IAttributeChangePropagator pch : prop){
        if(!propagateStruct && pch instanceof StructChangePropogator)
          continue;
        if(pch!=null && pch.shouldBeApplied(node, changes, Moment.BEFORE)) {
          attrChanged = pch.apply(node, changes, Moment.BEFORE) || attrChanged;
        }
      }
      //Map<String,AttributeDefinition> oldMap = storage.getAttributes(uuid);
      /*boolean save = false;
      for(String s:changes.getAll())
        if((AttributeDefinition.detectFlags(s)&Flag.VIRTUAL)!=Flag.VIRTUAL)
          save = true;
      if(save)*/
      if(attrChanged) {
        //TODO common commit or similar handling?
      }
      saveAttributes(node,changes,save, flag);
      attrChanged = false;
      for(IAttributeChangePropagator pch : node.getAPropogators()){
        if(!propagateStruct && pch instanceof StructChangePropogator)
          continue;
        if(pch.shouldBeApplied(node, changes, Moment.AFTER)) {
          attrChanged = pch.apply(node, changes, Moment.AFTER) || attrChanged;
        }
      }
      if(attrChanged) {
        //TODO common commit or similar handling?
      }
    }finally {
      stopChangeRow();
    }
  }
  public void saveAttributes(TreeNode node, ChangesSet<Attribute> changes, boolean save, byte flag){
    if(node.isDisposed())
      return;
//        System.out.println(1 ); 
    node.reactBeforeAttributeChange(changes, flag);
    boolean chf = !matchOld(changes.getNewMap(),changes.getOldMap());
    if(chf)
      handleTreeNodeChanges(node, changes, Moment.BEFORE, flag);
    if(!changes.isEmpty()){
      TreeDB.applyChanges(node.attrs(), changes);
      if(save)
        storage.saveAttributes(node.getUUId(), changes, flag);
    }else
      RLogger.logDebug( "null changes set for node "+node.getSUUId(),3);
    if(chf)
      handleTreeNodeChanges(node, changes, Moment.AFTER, flag);
  }
  private boolean matchOld(Map<String, Attribute> newMap, Map<String, Attribute> oldMap) {
    for(Entry<String, Attribute> en: oldMap.entrySet()) {
      Attribute anow = newMap.get(en.getKey());
      if(anow!=en.getValue()&&AttributeStorage.isAttributeDiffenent(anow, en.getValue()))
        return false;
    }
    return true;
  }


  /**
   * Method to set content of resource with name 'name' on element node
   *
   * @param node element to set resource for
   * @param name name of resource
   * @param in content of resource
   * @param data
   * @deprecated
   */
  public void setResourceContent(TreeNode node, String name, InputStream in, Object data, byte flag) {
    storage.setResourceContents(node.getUUId(), name, in, data, flag);
    // update Requirement name
    // if(node.getType().equals(Requirement.TYPE_NAME))
    // updateRequirementName(node);
    // TODO possible error
  }

  /**
   * Delete resource on element node with name "name"
   *
   * @param node element from which resource will be deleted
   * @param name name of resource
   */
  public void deleteResource(TreeNode node, String name, byte flag) {
    storage.deleteResource(node.getUUId(), name, flag);
  }

  /**
   * Method to check existence of resource with name "name" on element node
   *
   * @param node element where resource can be
   * @param name name of resource to check
   * @return true if resource exists, false otherwise
   */
  public boolean isResourceExists(TreeNode node, String name) {
    return storage.isResourceExists(node.getUUId(), name);
  }

  /**
   * Get name of linked to treeDB Storage
   *
   * @return name of storage
   */
  public String getLocalName() {
    if(localName!=null)
      return localName;
    return storage.getLocalName();
  }
  public String getVisibleName() {
    return storage.getVisibleName();
  }
  public String getProjectName() {
    if(projectName!=null)
      return projectName;
    String rn = tracker.getProjectName(this);
    if (rn != null) return rn; 
    return tracker.getProjectName(this);
  }

  public String getSemiLocalName() {
    if(localName!=null)
      return localName;
    String rn = tracker.getLocalName(this);
    if (rn != null) return rn; 
    return getVisibleName();
  }
  public String getSemiProjectName() {
    if(projectName!=null)
      return projectName;
    String rn = tracker.getProjectName(this);
    if (rn != null) return rn; 
    return getVisibleName();
  }
  // //////////////////////////////////////////////////////////////////////////
  //
  // Transaction API methods
  //
  // //////////////////////////////////////////////////////////////////////////
  public boolean startTransaction(String name) {
    return startTransaction(name, false);
  }

  /**
   * Method to start transaction - cancelable set of changes. Data storage will be replaces by
   * Transaction Storage. All changes will be applied on main storage only after commit.
   *
   * @param name name of transaction to show on undo\redo mechanics
   */
  public synchronized boolean startTransaction(String name, boolean check) {
    if(isDisposed) {
      RLogger.logError("Tries to start transaction '"+name+"' on disposed tree "+getLocalName()!=null?getLocalName():"NULL");
      return false;
    }
    if (isVirtual && Instantiator.getRootTree(this) != this) {
      return Instantiator.getRootTree(this).startTransaction(name, check);
    }
    TreeStateManager tsm = TreeStateManager.getOne(getTracker());
    String lname = getLocalName();
    String pname = getProjectName();
    RevisionInfo ri = tsm.getRevisionInfo(this);
    WriteLock wl = tsm.getLock(lname).writeLock();
    wl.lock();
    TreeState ts = tsm.getState(lname, ri);
    try{
      if(!tsm.canStartTransaction(lname, ri))
        return false;
//      if (inTransaction()) {
//        if (check) return false;
//        throw new IllegalStateException("transaction \"" + getVisibleName() + "\"is started");
//      }
      if(TreeState.isDisposed(ts))
        throw new IllegalStateException("Cannot start transaction in disposed state "+getVisibleName());
      tsm.setState(lname, pname, this, ri,TreeState.MODIFYING);
    }
    finally{
      wl.unlock();
    }
    if(CoreUtils.DEBUG_LEVEL>7)
      System.out.println("\t Start transaction "+getVisibleName()+" "+name);
    // saveLateChanges(null);
    startChangeRow();
    TreeInstancer vstorage = null;
    if (!isVirtual)
      vstorage = Instantiator.getVirtualStorage(this, Instantiator.DEFAULT_INSTANCE_NAME);
    TransactionStorage tStorage =
        new TransactionStorage(storage, vstorage, getTransactionListeners());
    tStorage.setName(name);
    tStorage.setDeltaTarget(storageDeltaListener);
    storage.setDeltaTarget(nullDeltaListener);
    //cacheStart  = storage.getCacheStorage().startCacheTransaction(name, 2);
    // if(vstorage!=null)
    // vstorage.setDeltaTarget(nullDeltaListener);
    storage = tStorage;
    // if(vstorage!=null){
    // TreeDB vdb =
    // Instantiator.getVirtualTreeDB(this,Instantiator.DEFAULT_INSTANCE_NAME);
    // vdb.startChangeRow();
    // vdb.storage = tStorage;
    // }
    return true;
  }

  public TransactionStorage commit() {
    return commit(true);
  }
  /**
   * Method to end transaction and apply changes on main storage.
   *
   * @return temporaty storage
   */
  
  public synchronized TransactionStorage commit(boolean addtoundo) {
    if (isVirtual && Instantiator.getRootTree(this) != this)
      return Instantiator.getRootTree(this).commit(addtoundo);
    if (!(storage instanceof TransactionStorage)) {
      throw new IllegalStateException("transaction not started");
    }
    String lname = getLocalName(), pname = getProjectName();
    try{
      if(CoreUtils.DEBUG_LEVEL>7)
        System.out.println("\t Stop transaction "+getVisibleName());
      // saveLateChanges(null);
      
      
      while(!que.isEmpty()){
        Set<Future<?>> toq = new HashSet<>(que);
        Iterator<Future<?>> it = toq.iterator();
        while(it.hasNext()){
          Future<?> r = it.next();
          try {
            if(!r.isCancelled()&&!r.isDone())
              r.get();
          } catch (InterruptedException e) {
            RLogger.logError(e);
          } catch (ExecutionException e) {
            RLogger.logError(e);
          }
          it.remove();
          que.remove();
        }
      }
      
      TransactionStorage tStorage = null;
      tStorage = (TransactionStorage) storage;
      
      storage = tStorage.getStorage();
      storage.markResourcesOutOfSync();
      storage.setDeltaTarget(storageDeltaListener);
      tStorage.setDeltaTarget(nullDeltaListener);
      tStorage.apply(addtoundo);
      storage.markResourcesSync();
      return tStorage;
    }
    finally{
      stopChangeRow();
      //TODO change state to one before transaction
      TreeStateManager.getOne(getTracker()).setState(lname, pname, this, TreeState.READY);
    }
  }

  /**
   * Cancel all changes after startTransaction. If transaction is not started will throw Exception
   */
  public synchronized void rollback() {
    if (isVirtual) {
      if(Instantiator.getRootTree(this)!=this)
        Instantiator.getRootTree(this).rollback();
      return;
    }
    String lname = getLocalName(), pname = getProjectName();
    try{
      while(!que.isEmpty()){
        Set<Future<?>> toq = new HashSet<>(que);
        Iterator<Future<?>> it = toq.iterator();
        while(it.hasNext()){
          Future<?> r = it.next();
          if(!r.isCancelled()&&!r.isDone())
            r.cancel(true);
          it.remove();
          que.remove();
        }
      }
      if (!(storage instanceof TransactionStorage)) {
        throw new IllegalStateException("transaction not started");
      }
  
      if(CoreUtils.DEBUG_LEVEL>7)
        System.out.println("\t Rollback "+getVisibleName());

      TransactionStorage tStorage = (TransactionStorage) storage;
      storage = tStorage.getStorage();
      storage.setDeltaTarget(storageDeltaListener);
      tStorage.setDeltaTarget(nullDeltaListener);
      tStorage.rollback();
    }finally{
      stopChangeRow();
      TreeStateManager.getOne(getTracker()).setState(lname, pname, this, TreeState.READY);
    }
  }

  /**
   * Add listener on transactions events
   *
   * @param l listener to add
   */
  public void addTransactionListener(ITransactionListener l) {
    TreeDB root = this;
    if(this.isVirtual)
      root = Instantiator.getRootTree(this);
    if (root.equals(this)) transactionListeners.add(l);
    else root.addTransactionListener(l);
  }

  /**
   * Remove listener from transaction listeners
   *
   * @param l listener to remove
   */
  public void removeTransactionListener(ITransactionListener l) {
    TreeDB root = this;
    if(root.isVirtual)
      root = Instantiator.getRootTree(this);
    if (root.equals(this)) transactionListeners.remove(l);
    else root.removeTransactionListener(l);
  }

  // ////////////////////////////////////////////////////////////////////////
  //
  // Types operations
  //
  // ////////////////////////////////////////////////////////////////////////
  public void addNodeType(String name, Class<? extends TreeNode> clazz) {
    if (isVirtual) {
      // TreeDB db = Instantiator.getRootTree(this);
      // if(db!=null)
      // db.addNodeType(name,clazz);
    } else {
      NodeType nt = new NodeType(name, TreeDB.this);
      nt.setClass(clazz);
      defaultTypes.put(name, nt);
      initNodeAttributes(nt,nt.attrs().getChanged().getNewMap(), true, OFlag.NONE);
    }
  }

  public void addNodeType(Class<? extends TreeNode> clazz) {
    if (isVirtual) {
      TreeDB db = Instantiator.getRootTree(this);
      if (db != null && db != this) db.addNodeType(clazz);
    } else addNodeType(clazz.getSimpleName(), clazz);
  }

  public void addNodeTypes(Map<String, Class<? extends TreeNode>> nts) {
    if (isVirtual) {
      TreeDB db = Instantiator.getRootTree(this);
      if (db != null && db != this) db.addNodeTypes(nts);
    } else
      for (String name : nts.keySet()) {
        addNodeType(name, nts.get(name));
      }
  }

  /**
   * Method to get map of element types in form of pairs String type name -> NodeType
   *
   * @return available element types
   */
  public Map<String, NodeType> getTypes() {
    if (isVirtual) {
      TreeDB db = Instantiator.getRootTree(this);
      if (db != null && db != this) return db.getTypes();
    }
    Map<String, NodeType> result = new HashMap<>(defaultTypes);
    TreeNode typesNode = getRootNode().findWorkChild("Types");
    if (typesNode != null && typesNode.sizeWorkChildren() > 0) {
      int num = typesNode.sizeWorkChildren();
      for (int i = 0; i < num; i++) {
        NodeType node = (NodeType) typesNode.getWorkChild(i);
        result.put(node.getId(), node);
      }
    }
    return result;
  }

  public void createType(NodeType newType) {

    if (isVirtual) Instantiator.getRootTree(this).createType(newType);
    else {
      TreeNode types = getNode("/Types", Path.QID);
      if (types == null) {
        types = CreateNode.call(getRootNode(), NodeType.TYPE_NAME,"Types");
      }
      TreeNode newTypeNode = types.createChild(newType.getName());
      newTypeNode.commitAttributes();
    }
  }

  public NodeType getNodeType(String name) {
    if (isVirtual) {
      TreeDB db = Instantiator.getRootTree(this);
      if (db != null && db != this) return db.getNodeType(name);
    }
    for (NodeType type : getTypes().values()) {
      if (type.getId().equals(name)) {
        return type;
      }
    }
    return null;
  }

  public boolean removeType(String typeName) {
    if (isVirtual) {
      TreeDB db = Instantiator.getRootTree(this);
      if (db != null && db != this) return db.removeType(typeName);
    }
    TreeNode types = getNode("/Types", Path.QID);
    if (types == null) {
      return false;
    }
    TreeNode n = types.findWorkChild(typeName);
    n.delete();
    return true;
  }

  // ////////////////////////////////////////////////////////////////////////
  //
  // TreeDB factories
  //
  // ////////////////////////////////////////////////////////////////////////

  /**
   * Creates db structures and root node
   *
   * @param name
   * @param storage
   * @param uuidGenerator
   * @return
   */
  public static TreeDB createTreeInstance(String localname, String projectname, IResTracker tracker, ITreeStorage storage, IUUIDGenerator uuidGenerator, byte flag) {
    //TODO maybe refactor it
    TreeDB newDB = new TreeDB(tracker, 0);//, storage);
    newDB.addTreeStateHooks(localname, projectname);
    //TreeStateManager.getOne().setState(localname, (RevisionInfo)null, TreeState.LOADING);
    if(uuidGenerator == null)
      uuidGenerator = new DefaultUUIDGenerator();
    newDB.uuidGenerator = uuidGenerator;
    newDB.localName = localname;
    storage.createStorage(localname, projectname);//Create project itself
    newDB.storage = storage;
    newDB.initRootTypes();
    tracker.initClasses(newDB);
    // create root node
    try {
      storage.initCaches(storage, CacheInitializationStage.BEFORE_TRACK, newDB.getMonitor());//TODO move?
    } catch (Exception e) {
      e.printStackTrace();
    }
    if(storage.getRootNode() == null){
      UUID uuid = uuidGenerator.nextUUId();
      storage.createNode(null, uuid, "DBRoot", "/", (String)null, flag);
    }
    
    createTreeStructure(newDB);
    
    List<String> vers = new ArrayList<>();
    vers.add(StoredFormatVersioning.getOne().getFullVersionByGroupId(TreeNode.NODE_GROUP));
    StoredFormatVersioning.getOne().setFormatsVersions(new File(storage.getLocation()), vers);
    //created project all seems to be non-revision one
    reactOnTreeLoaded(newDB, localname, storage);
    
    return newDB;
  }



  private static void createTreeStructure(TreeDB newDB) {
    ResManager.createProjectStructure(newDB);
  }


  /**
   * Creates db structures and root node
   *
   * @param name
   * @param storage
   * @return treeDB object
   */
  public static TreeDB createTreeInstance(String lname, String pname, IResTracker tracker, ITreeStorage storage) {
    return createTreeInstance(lname,pname, tracker, storage, new DefaultUUIDGenerator(), OFlag.NONE);//TODO flag?
  }
  
  public static TreeDB loadTreeInstance(String localName,String projectName,RevisionInfo ri, IResTracker tracker, ITreeStorage storage) {
    return loadTreeInstance(localName,projectName, ri, tracker, storage, new DefaultUUIDGenerator());
  }
  public static TreeDB loadTreeInstance(String lname, String pname, RevisionInfo ri, IResTracker tracker, ITreeStorage storage, IUUIDGenerator uuidGenerator) {
    return loadTreeInstance(lname,pname, ri, tracker, storage, uuidGenerator, 0, null);
  }

  public static TreeDB loadTreeInstance(String lname, String pname, RevisionInfo ri, IResTracker tracker, 
      ITreeStorage storage, IUUIDGenerator uuidGenerator, int loa, TreeDB base) {
    TreeDB newDB = new TreeDB(tracker, loa);
    //newDB.addTreeStateHooks(lname, pname);
    newDB.uuidGenerator = uuidGenerator;
    newDB.storage = storage;
    tracker.initClasses(newDB);
    if(lname == null)
      lname = storage.getLocalName();
    newDB.localName = lname;
    if(pname == null)
      pname = storage.getProjectName();
    newDB.projectName = pname;
    newDB.initRootTypes();
    reactOnTreeLoaded(newDB, lname, storage);
    // FIXME: for test we can use mock target, so we left old one if it is
    // not null. Its look like bad design.
    return newDB;
  }

//  public void updateLocation(URI location) {
//    storage.updateLocation(location);
//  }
  private static void reactOnTreeLoaded(TreeDB newDB, String flname, ITreeStorage storage) {
    if (storage.getDeltaTarget() == null || newDB.storageDeltaListener!=storage.getDeltaTarget() && newDB.storageDeltaListener!=newDB.nullDeltaListener) {
      storage.setDeltaTarget(newDB.storageDeltaListener);
    }else
      RLogger.logDebug("Old resStorage was used during loading of "+flname);
    
    SecureStorage.getOneWithDefPass(newDB);
    //container=CheckerContainer.init(tracker, newDB, ri);
  }
  

  // ////////////////////////////////////////////////////////////////////////
  /**
   * Add listener on element changes
 * @param listener listener on element changes
   *
   * @return true if listener is added, false otherwise
   */
  public boolean addListener(ITreeChangeListener listener) {
    return getListenersController().addListener(getLocalName(), listener);
  }
  /**
   * Remove listener on element changes
 * @param listener listener to remove
   *
   * @return true if listener is removed, false otherwise
   */
  public boolean removeListener(ITreeChangeListener listener) {
    return getListenersController().removeListener(getLocalName(), listener);
  }

  /**
   * Get list of listeners on tree changes.
   *
   * @return list of listeners
   */
  
  private static String META_LISTENERS_CONTROLLER = "LISTENERS_CONTROLLER"; 
  private ListenersController getListenersController() {
    ListenersController lc = getMetaObject(META_LISTENERS_CONTROLLER);
    if(lc == null) {
      synchronized (META_LISTENERS_CONTROLLER) {
        lc = getMetaObject(META_LISTENERS_CONTROLLER);
        if(lc == null) {
          lc = new ListenersController();
          registerMetaObject(META_LISTENERS_CONTROLLER, lc);
        }
      }
    }
    return lc;
  }
  
  //TODO move to separate class
  public List<ITreeChangeListener> getListeners() {
    return getListenersController().getListeners(getLocalName());
  }

  public List<ITreeChangeListener> getGUIListeners() {
    return getListenersController().getGUIListeners(getLocalName());
  }

  // ////////////////////////////////////////////////////////////////////////
  public boolean addGUIListener(ITreeChangeListener listener) {
    return getListenersController().addGUIListener(getLocalName(), listener);
  }

  public boolean removeGUIListener(ITreeChangeListener listener) {
    return getListenersController().removeGUIListener(getLocalName(), listener);
  }
  
  private IStorageDeltaListener nullDeltaListener =
      new IStorageDeltaListener() {

        @Override
        public void startChangeRow() {
        }

        @Override
        public void stopChangeRow() {
        }

        @Override
        public void created(UUID uuid, String template, byte flag) {
        }

//
//        @Override
//        public void changed(
//            UUID uuid,
//            ChangesSet<Attribute> what,
//            Map<String, AttributeDefinition> newAttrs,
//            Moment m,
//            boolean forceInternal) {
//          // TODO Auto-generated method stub
//
//        }

        @Override
        public void resourceChanged(UUID uuid, String resName, Moment m, byte flag) {
        }

        @Override
        public void moved(
            UUID uuid, UUID oldParent, UUID newParent, Moment m, byte flag) {
        }

        @Override
        public void beforeCreated(
            UUID uuid, UUID par, String template, Map<String,AttributeDefinition> attrs, byte flag) {
        }

        @Override
        public void versionChanged(
            UUID uuid,
            ChangesSet<Version> changes,
            Moment m, 
            byte flag) {
        }

        @Override
        public boolean changeAttributes(
            UUID uuid, ChangesSet<Attribute> what, byte flag) {
          return false;
        }

        @Override
        public ITreeStorage operationStarted(String name, String opId) {
          return null;
        }

        @Override
        public void operationEnded(String opId) {
          if(operationId!=null&& opId.equals(operationId)){
            try{
              commit(false);
            }finally{
              operationId = null;
            }
          }
        }

        @Override
        public ITreeStorage getStorage() {
          // TODO Auto-generated method stub
          return null;
        }

        @Override
        public void created(NodeDescSet nd, String template, byte flag) {
        }

        @Override
        public void beforeCreated(NodeDescSet desc, String template, byte flag) {
        }

        @Override
        public void deleted(NodeDescSet nd, byte flag) {
        }

        @Override
        public void beforeDeleted(NodeDescSet desc, byte flag) {
        }
      };

  private void notifyExternalChange() {
    for (ITransactionListener tl : new ArrayList<>(transactionListeners)) {
      tl._dispose();
    }
  }

  private boolean isSameClone(TreeNode node, NodeDesc desc) {
    return isSameClone(node, desc.getAttributes());
  }

  private boolean isSameClone(TreeNode node, TreeNode desc) {
    return isSameClone(node, desc.attrs().getActiveAttributeDefs());
  }

  private boolean isSameClone(TreeNode node, Map<String, AttributeDefinition> atrs) {
    boolean descVirt =
        !atrs.containsKey(TreeNode.ATTR_VIRT_STATUS)
            ? false
            : atrs.get(TreeNode.ATTR_VIRT_STATUS) == null
                ? false
                : atrs.get(TreeNode.ATTR_VIRT_STATUS).getRawValue().getValue() != null;
    // TODO compare trees
    if (!descVirt) return false;
    String cloneid =
        !atrs.containsKey(TreeInstancer.ATTR_CLONE_ID)
            ? ""
            : atrs.get(TreeInstancer.ATTR_CLONE_ID) == null
                ? ""
                : getAttributeValue(node.getUUId(), atrs.get(TreeInstancer.ATTR_CLONE_ID), "", true).toString();
    String clonepar =
        !atrs.containsKey(TreeNode.ATTR_CLONE_PARENT)
            ? ""
            : atrs.get(TreeNode.ATTR_CLONE_PARENT) == null
                ? ""
                : getAttributeValue(node.getUUId(), atrs.get(TreeNode.ATTR_CLONE_PARENT), "", true)
                    .toString();
    // atrs.get(TreeInstancer.ATTR_CLONE_PARENT).iterator().next().getValue().toString();
    return (node.isVirtual()
        && isVirtual
        && cloneid.equals(node.attrs().getAttributeValue(TreeInstancer.ATTR_CLONE_ID, ""))
        && clonepar.equals(node.getCloneParentUUId().toString()));
  }
  private ChangesSet<Attribute> updateParentCloneParentAttributes(
      UUID nodeUUID, TreeNode node, UUID cp, Map<String, Attribute> attrs) {
    UUID cloneP = cp;
    if (isVirtual && cp == null) cloneP = TreeDB.this.getVStorage().getCloneParent(cp);
    Map<String, Attribute> cpa = null;
    TreeNode cloneParent = null;
    if (cloneP == null) { // TODO mb remove
      Object cpo =
          AttributeStorage.getAttributeValue(
              TreeDB.this, attrs, nodeUUID, TreeNode.ATTR_CLONE_PARENT);
      if (cpo != null) {
        cloneP = UUID.fromString(cpo.toString());
      }
    }
    if (cloneP != null) {
      try {
        cloneParent = getNode(cloneP);
        if (cloneParent != null) {
          if (cloneParent.getVS().isCloneParentVersionOutdated(Type.ATTRIBUTES))
            cloneParent
                .getTreeDB()
                .updateParentCloneParentAttributes(
                    cloneParent.getUUId(),
                    cloneParent,
                    null,
                    cloneParent.attrs().getLocalAttributes());
        }
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
    if (cloneParent != null
        && (node == null
            || VersionsStorage.isCloneParentVersionOutdated(
                this.isVirtual?Instantiator.getRootTree(this):null, Type.ATTRIBUTES, nodeUUID, cloneP))) {
      cpa = cloneParent.attrs().getLocalAttributes();
      if (node != null) node.attrs().getVersionsStorage().updateCloneParentVersion(Type.ATTRIBUTES);
    }
    if (attrs == null) RLogger.logInfo("null attributes! " + nodeUUID);
    if (attrs != null && (cpa != null)) {
      // VersionsStorage.initOrLoad(Instantiator.getRootTree(this).getStorage(),
      // node).incVersion(Type.ATTRIBUTES);
      Set<String> keys = new HashSet<>(attrs.keySet());
      //if (pa != null) keys.addAll(pa.keySet());
      keys.addAll(cpa.keySet());
      ChangesSet<Attribute> changes = new ChangesSet<>();
      for (String s : keys) {
        if (!s.startsWith("_%")) {
          Attribute old = attrs.get(s);
          attrs.put(
              s,
              AttributeStorage.mergeAttrs(
                  this,
                  nodeUUID,
                  new AttributeToMerge(
                      cpa.get(s),
                      MergeParams.ALL,
                      true),
//                  new AttributeDefToMerge(
//                      !s.startsWith("_%") && pa != null ? pa.get(s) : null,
//                      MergeParams.NOTLOCAL,
//                      true),
                  new AttributeToMerge(attrs.get(s), MergeParams.ALLHP, false)));
          if (attrs.get(s) == null) attrs.remove(s);
          try {
            changes.addSomeChange(s,old,attrs.get(s));
          } catch (OrderWasAlreadySetException e) {
            RLogger.logError(e);
          }
          //TODO change
        }
      }
      return changes;
    }
    return null;
  }
  private void updateParentCloneParentAttributeDefs(
      UUID nodeUUID, AttributeStorage as, UUID cp, UUID parent, Map<String, AttributeDefinition> attrs) {
    UUID cloneP = cp;
    if (isVirtual && cp == null) cloneP = TreeDB.this.getVStorage().getCloneParent(cp);
    Map<String, AttributeDefinition> cpa = null, pa = null;
    TreeNode cloneParent = null;
    if (cloneP == null) { // TODO mb remove
      Object cpo =
          AttributeStorage.getAttributeDefValue(
              TreeDB.this, attrs, nodeUUID, TreeNode.ATTR_CLONE_PARENT);
      if (cpo != null) {
        cloneP = UUID.fromString(cpo.toString());
      }
    }
    if (cloneP != null) {
      try {
        cloneParent = getNode(cloneP);
        if (cloneParent != null) {
          if (cloneParent.getVS().isCloneParentVersionOutdated(Type.ATTRIBUTES))
            cloneParent
                .getTreeDB()
                .updateParentCloneParentAttributeDefs(
                    cloneParent.getUUId(),
                    cloneParent.attrs(),
                    null,
                    cloneParent.getWorkParentUUID(),
                    cloneParent.attrs().getRawDMap());
        }
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
    if (cloneParent != null
        && (as == null
            || VersionsStorage.isCloneParentVersionOutdated(
                this.isVirtual?Instantiator.getRootTree(this):this, Type.ATTRIBUTES, nodeUUID, cloneP))) {
      cpa = cloneParent.attrs().getActiveAttributeDefs();
      if (as != null) as.getVersionsStorage().updateCloneParentVersion(Type.ATTRIBUTES);
    }
    if(parent!=null){
      TreeNode par = getCachedNodeOrVirt(parent);
      if(par!=null)
        pa = par.attrs().getPropogatingAttributeDefs();
    }
    if (attrs == null) RLogger.logInfo("null attributes! " + nodeUUID);
    if (attrs != null && (cpa != null || pa != null)) {
      Set<String> keys = new HashSet<>();
      if (pa != null) keys.addAll(pa.keySet());
      if (cpa != null) keys.addAll(cpa.keySet());
      for (String s : keys) {
        if (!s.equals(AttributesVersioningRule.ATTR_NODE_VERSION)&&!s.startsWith("_%")) {
          attrs.get(s);
          attrs.put(
              s,
              AttributeStorage.mergeAttrDefs(
                  this,
                  nodeUUID,
                  new AttributeDefToMerge(
                      !s.startsWith("_%") && cpa != null ? cpa.get(s) : null,
                      MergeParams.ALL,
                      true),
                  new AttributeDefToMerge(
                      !s.startsWith("_%") && pa != null ? pa.get(s) : null,
                      MergeParams.NOTLOCAL,
                      true),
                  new AttributeDefToMerge(attrs.get(s), MergeParams.ALLHP, false)));
          if (attrs.get(s) == null) attrs.remove(s);
        }
      }
    }
  }
  private IStorageDeltaListener storageDeltaListener =
      new IStorageDeltaListener() {
        private IStorageDeltaListener getProperListener(
            UUID uuid, Map<String, AttributeDefinition> attrs) {
          TreeNode curNode = getCachedNodeOrVirt(uuid);
          if (attrs == null) attrs = storage.getAttributes(uuid);
          if (!isVirtual) {
            if (attrs == null) attrs = storage.getAttributes(uuid);
            if (attrs != null) {
              AttributeDefinition la = attrs.get(TreeNode.ATTR_VIRT_STATUS);
              if (la != null && la.getRawValue()!=null && la.getRawValue().getValue() != null
                  || curNode != null && curNode.isVirtual()) {
                boolean nvirt = false;
                // if(attrs.containsKey(TreeNode.ATTR_VIRT_STATUS)&&!attrs.get(TreeNode.ATTR_VIRT_STATUS).isEmpty()){
                Object val = getAttributeValue(uuid, la, null, false);
                if (val == null) nvirt = true;
                else {
                  TreeDB db =
                      Instantiator.getExistVirtualTreeDB(TreeDB.this, val.toString());
                  if (db != null && db != TreeDB.this) { // null if
                    // root is
                    // creating
                    // right now
                    return db.getDeltaTarget();
                  }
                }
                // }
                if (nvirt) {
                  nvirt = true;
                  return TreeDB.this
                      .getDeltaTarget(); // Instantiator.getVirtualTreeDB(TreeDB.this,Instantiator.DEFAULT_INSTANCE_NAME).getDeltaTarget();
                }
              }
            }
          }
          return this;
        }

        @Override
        public void beforeCreated(
            NodeDescSet rt, String templ, byte flag) {
          NodeDesc root = rt.root;
          UUID node = root.getUUId();
          if(CoreUtils.DEBUG_LEVEL>7)
            RLogger.logDebug("Before creation of "+node+" on "+ResManager.getManager().getVisibleName(TreeDB.this)); 
          
          String newId = root.getId();
          IStorageDeltaListener lis = getProperListener(node, root.getAttributes());
          if (lis == null) return;
          if (lis != this) {
            if(!OFlag.isNoUpdate(flag))
              lis.beforeCreated(rt, templ, flag);
            return;
          }
          UUID uid = storage.getRootNode();
          UUID par = root.getWorkParentUUID(), prev = uid;
          while(par!=null && !uid.equals(par))
          {
            prev = par;
            par = storage.getParent(par);
            
          }
          
          startChangeRow();
          try {
            TreeNode reqsRoot = null;
            boolean isRequirementsTree = storage.canLoad(prev) && storage.getNodeId(prev).equals("Requirements");
            if(isRequirementsTree)
              reqsRoot = getNode(prev);
            
            LinkedList<NodeDesc> nds = rt.allNodesCreate;
            ListIterator<NodeDesc> li = nds.listIterator();
            TreeNode rootReq = null;
            while(li.hasNext()) {
              NodeDesc chi = li.next();
              if(getCMM().getNewIndexesManipulator().isRemovedNew(chi.getUUId()))
                getCMM().getNewIndexesManipulator().addNewIndex(TreeDB.this,chi.getUUId());
              if(isRequirementsTree) {
                TreeNode reqRoot = FIDUpdater.initNodeDesc(TreeDB.this, reqsRoot, chi.getUUId(), chi.getAttributes());
                if(reqRoot!=null)
                  rootReq = reqRoot;
              }
            }
            if(rootReq!=null)
              rootReq.commitAttributes();
            li = nds.listIterator();
            while(li.hasNext()) {
              NodeDesc lit = li.next();
              UUID uuid = lit.getUUId();
              UUID parent = lit.getWorkParentUUID();
              Map<String, AttributeDefinition> attrs = lit.getAttributes();
              AttributeDefinition ad = attrs.get(TreeNode.ATTR_TYPE);
              String type = null;
              if(ad != null)
                type = ad.getRawValue().getValue().toString();
              
              // saveLateChanges(getCachedNodeOrVirt(parent));
              Object cp =
                  AttributeStorage.getAttributeDefValue(
                      TreeDB.this, attrs, uuid, TreeNode.ATTR_CLONE_PARENT);
              UUID cpu = null;
              if (cp != null) cpu = UUID.fromString(cp.toString());
              updateParentCloneParentAttributeDefs(uuid, null, cpu, parent, attrs);
              
              TreeNode template = null;
              if(templ==null || !templ.equals(EMPTY_TEMPLATE)) {
                if (templ != null) {
                  template = getTemplate(templ);
                } else 
                  if(type!=null)
                    template = getActiveTemplate(type);
              }
              if (template != null) {
                for (Attribute a : template.attrs()) {
                  //TODO check textNode types
                  if(attrs.containsKey(a.getKey())) continue;
                  //if (node.attrs().getLocalAttributeKeys().contains(a.getKey())) continue;
                  //changed = true;
                  //if (a.getOrigin().equals(template.getUUId()))
                  attrs.put(a.getKey(), (new AttributeDefinition(uuid, a.getKey(),a.getAvailability(),
                    a.getDefinition().getValueProvider(),
                    a.getDefinition().getFlags())));
                    //.storeAttribute(a.copy(node.getUUId(), node.getUUId()));
                 // else node.attrs().storeAttribute(a.copy(node.getUUId()));
                }
              }
              
              if(CoreUtils.DEBUG_LEVEL>7)
                RLogger.logInfo("Before creation of "+uuid+" on "+ResManager.getManager().getVisibleName(TreeDB.this)+" ended"); 
            }
            li = nds.listIterator(nds.size());
            while(li.hasPrevious())
            {
              NodeDesc nda = li.previous();
              if(!OFlag.isNoUpdate(flag)) {
                List<ITreeChangeListener> list = getListeners();
                for (ITreeChangeListener l : list) 
                  if (l != null) 
                  //if(cache.get(nda.getUUId()) == null) {
                    l.beforeCreated(nda, flag);
                  //}
              }
              if(!OFlag.isNoGuiUpdate(flag))
                addGuiChange(
                  new TreeNodeChangeEvent(
                    TreeNodeChangeEventType.CREATE, Moment.BEFORE, nda, flag));
            }
          }finally{
            stopChangeRow();
          }
        }


        @Override
        public void created(NodeDescSet rt, String templ, byte flag) {
          NodeDesc root = rt.root;
          UUID node = root.getUUId();
          TreeNode curNodeRoot = getCachedNodeOrVirt(node);
          Map<String, AttributeDefinition> satrs;
          String newId = root.getId();
          //возможно добавить dummy в кэш до этапа created
          //вот тут раньше глушило если объект найден в кеше, но он иногда всегда находится(при первичной загрузке проекта с обновлением версии) потому нужно подумать
          if (curNodeRoot != null) {
            satrs = curNodeRoot.attrs().getActiveAttributeDefs();
          }else
            satrs = root.getAttributes();
          
          //Map<String, AttributeDefinition> satrs = root.getAttributes();
          IStorageDeltaListener lis = getProperListener(node, satrs);
          if (lis == null) return;
          if (lis != this) {
            if(!OFlag.isNoUpdate(flag))
              lis.created(rt, templ, flag);
            return;
          }
          
          LinkedList<NodeDesc> addedNodes = rt.allNodesCreate;
          LinkedList<NodeDesc> notLoaded = new LinkedList<>();
          LinkedList<TreeNode> loaded = new LinkedList<>();
          ListIterator<NodeDesc> li = addedNodes.listIterator();
          while(li.hasNext())
          {
            NodeDesc nd = li.next();
            TreeNode cached = getCachedNodeOrVirt(nd.getUUId());
            if(cached == null) {
              notLoaded.add(nd);
             // l.beforeCreated(nd, forceInternal);
            }else
              loaded.add(cached);
          }
//          curNode = cache.get(node);
//          if (curNode != null) {
//            return;
//          }
          startChangeRow();

          UUID uid = storage.getRootNode();
          UUID par = root.getWorkParentUUID(), prev = uid;
          while(par!=null && !uid.equals(par))
          {
            prev = par;
            par = storage.getParent(par);
          }
          String type = root.getType();
          List<Location> locs = null;
          try{
              TreeNode reqsRoot = null;
              boolean isRequirementsTree = storage.canLoad(prev) && storage.getNodeId(prev).equals("Requirements");
              if(isRequirementsTree)
                reqsRoot = getNode(prev);
              List<TreeNode> cnodes = new LinkedList<>();
              for(NodeDesc nd2: notLoaded) {
                uid = nd2.getUUId();
                TreeNode curNode = cache.get(uid);
                if (curNode != null) {
                  continue;
                }
                curNode = getNode(nd2, true);//adds check ignore to ignore errors on mid stage 
                if (curNode == null) continue;
                curNode.parent = nd2.getWorkParentUUID();
                loaded.add(curNode);
              }
              for(TreeNode curNode:loaded)
              {
                LinkResolver.resetLinksObject(curNode);
                cnodes.add(curNode);
                TreeNode parent = curNode.getWorkParent();
                // add
                // before
                // clone
                // upds
                if (parent != null && !parent.containsWorkChildUUID(node)) // test
                  // here
                  // for
                  // stranges
                  parent.addChildUUID(curNode.getUUId());
      
                // update Requirement name in cache
    
                updateRequirementName(curNode);
                if (!OFlag.isNoUpdate(flag)) notifyExternalChange();
                if (curNode instanceof ITreeChangeListener) addListener((ITreeChangeListener) curNode);
                if (curNode instanceof INodeChangeListener)
                  curNode.addChangeListener((INodeChangeListener) curNode);
              }
              for(TreeNode curNode: cnodes) 
                if (Requirement.TYPE_NAME.equals(curNode.getType())) {
                  Location.markLocations(curNode, null);
                  for (Location l : ((Requirement) curNode).getLocations(true)) {
                    if(locs == null)
                      locs = new LinkedList<>();
                    if(!locs.contains(l))
                      locs.add(l);
                  }
                }
              if(reqsRoot!=null)
                reqsRoot.attrs().commitAttributes();//commit maxid change
          }
          finally{
            nodeCreated(uid, type, flag);
            li = addedNodes.listIterator();
            while(li.hasNext())
            {
              NodeDesc nd = li.next();
              TreeNode nde =  getNode(nd, false);
              if(!OFlag.isNoUpdate(flag)) {
              List<ITreeChangeListener> list = getListeners();
                for (ITreeChangeListener l : list) 
                  if (l != null)
                    l.created(nde, flag);
              }
              if(!OFlag.isNoGuiUpdate(flag))
                addGuiChange(
                  new TreeNodeChangeEvent(
                    TreeNodeChangeEventType.CREATE, Moment.AFTER , nde, flag)); // do
            }
            if(locs!=null)
              for(Location l : locs)
                l.resetLabel(); // TODO
            stopChangeRow();
          }
        }


        /** Reaction on element deletion */
        @Override
        public void deleted(NodeDescSet rt, byte flag) {
          NodeDesc root = rt.root;
          UUID rootuid = root.getUUId();
          TreeNode node = getCachedNodeOrVirt(rootuid);
          if(CoreUtils.DEBUG_LEVEL>7)
            RLogger.logDebug("TreeDB delete of "+root.getUUId()+" on "+ResManager.getManager().getVisibleName(TreeDB.this)); 
          
          if(node == null) {
            TreeDB db = TreeDB.this;
            if (db.isVirtual) db = Instantiator.getRootTree(db);
            node = db.getCachedNodeOrVirt(rootuid);
            if (node != null) {
              IStorageDeltaListener delta =
                  getProperListener(rootuid, node.attrs().getActiveAttributeDefs());
              if (delta != null) {
                if (delta != this) {
                  delta.deleted(rt, flag);
                  return;
                }
              } else
                return;
            }
            else
              return;//Node not found
          }
            //TODO may be problems with virt nodes?
          List<NodeDesc> nds = rt.allNodesRemove;
          startChangeRow();
          for(NodeDesc nd:nds){
            UUID uuid = nd.getUUId();
            if(getCMM().getNewIndexesManipulator().isNew(uuid)){
              getCMM().getNewIndexesManipulator().addRemovedNewIndex(TreeDB.this,uuid);
              getCMM().getNewIndexesManipulator().removeNewIndex(TreeDB.this,uuid);
            }
          }
          try{
            TreeNode rnode = node;
            TreeDB rootDB = Instantiator.getRootTree(TreeDB.this);
            List<TreeNode> nodes = null;
            List<Location> locs = null;
            for(NodeDesc nd:nds){
              UUID uuid = nd.getUUId();
              UUID par = nd.getWorkParentUUID();
              if (par != null) {
                try {
                  TreeNode wp = rnode.getTreeDB().getNodeExc(par);
                  if (wp != null && !wp.isDisposed()) wp.removeChildUUID(uuid, flag);
                }catch(NodeNotFoundException nfe) {
                }
              }
              for (TreeNode nda : Instantiator.getClones(rootDB, nd)) {
                try {
                  nda.delete();
                  // ti.remove(uid);
                } catch (Exception e) {
                  // clone del error
                }
              }
              node = getCachedNodeOrVirt(uuid);
              if(node == null)
                continue;//TODO test
              if(nodes == null)
                nodes = new LinkedList<>();
              nodes.add(node);
              if (VirtualNode.TYPE_NAME.equals(node.getType())) {
                // TODO move
                if (Instantiator.revreuses.containsKey(node)) {
                  UUID uid = Instantiator.revreuses.remove(node);
                  Instantiator.reuses.get(uid).remove(node);
                  if (Instantiator.reuses.get(uid).isEmpty()) Instantiator.reuses.remove(uid);
                }
              }
              
              if (node instanceof ITreeChangeListener) removeListener((ITreeChangeListener) node);

              if(!OFlag.isNoUpdate(flag))
                node.fireNodeDelete();
              if(!OFlag.isNoUpdate(flag)) {
              List<ITreeChangeListener> list = getListeners();
                for (ITreeChangeListener l : list) 
                  l.deleted(node, flag, Moment.AFTER);
                
              }
              if(!OFlag.isNoGuiUpdate(flag))
                  addGuiChange(
                    new TreeNodeChangeEvent(TreeNodeChangeEventType.DELETE, Moment.AFTER, node, flag, node));
                
            }
            for(NodeDesc nd:nds) {
              TreeNode node2 = clearNodeCache(nd.getUUId(), nd.getWorkParentUUID(), DEFAULT_DEPTH);
              if (node2!=null && node2 instanceof Requirement) 
              {
                Requirement r = (Requirement) node2;
                if(r.attrs().getAttribute(Requirement.ATTR_LOCATIONS)!=null) {
                  for(Location l:r.getLocations()) {
                    if(locs==null)
                      locs = new LinkedList<>();
                    if(!locs.contains(l))
                      locs.add(l);
                  }
                }
              }
            }
            if (!OFlag.isNoUpdate(flag)) {
              notifyExternalChange();
            }
            if(CoreUtils.DEBUG_LEVEL>7)
              RLogger.logDebug("TreeDB delete fin on "+ResManager.getManager().getVisibleName(TreeDB.this) );
            if(locs!=null)
              for(Location l:locs)
                l.resetLabel();
          }
          finally{
            stopChangeRow();
          }
          
        }
        /** Reaction on element deletion */
        @Override
        public void beforeDeleted(NodeDescSet rt, byte flag) {
          NodeDesc nd = rt.root;
          UUID root = nd.getUUId();
          TreeNode node = getCachedNodeOrVirt(root);
          if(node!=null && node.attrs().hasChanges())
              node.commitAttributes();//commit changes before delete
          if(CoreUtils.DEBUG_LEVEL>7)
            RLogger.logDebug("TreeDB beforeDelete of "+nd.getUUId()+" on "+ResManager.getManager().getVisibleName(TreeDB.this)); 
          if (node == null) {
            TreeDB db = TreeDB.this;
            if (db.isVirtual) db = Instantiator.getRootTree(db);
            node = db.getCachedNodeOrVirt(root);
            if (node != null) {
              IStorageDeltaListener delta =
                  getProperListener(root, node.attrs().getActiveAttributeDefs());
              if (delta != null) {
                if (delta != this) {
                  delta.beforeDeleted(rt, flag);
                  return;
                }
              } else
                return;
            }
            else
              return;//Node not found
          }
          if (node == null || node.getType() == null) {
            return;
          }
          startChangeRow();
          try{
            List<NodeDesc> nds = rt.allNodesRemove;
            ListIterator<NodeDesc> li = nds.listIterator();
            while(li.hasNext()) {
              NodeDesc nda = li.next();
              UUID uuid = nda.getUUId();
              nda.getType();
              
              node = getCachedNodeOrVirt(uuid);
              if(node == null)
              try {
                  node = getNode(uuid,TreeDB.DEFAULT_DEPTH, true, false);//loading, can cause some troubles
              }catch(NodeNotFoundException nnfe) {
              }
              if(node == null) {
                RLogger.logDebug("Null node for "+uuid+" during remove, possible while undo", 8);
              }
              else {
                if(!OFlag.isNoGuiUpdate(flag))
                  addGuiChange(
                    new TreeNodeChangeEvent(TreeNodeChangeEventType.DELETE, Moment.BEFORE, node, flag, node));
                if(!OFlag.isNoUpdate(flag)) {
                  List<ITreeChangeListener> list = getListeners();
                  for (ITreeChangeListener l : list) 
                    l.deleted(node, flag, Moment.BEFORE);
                }
              }
              //TreeNode fnode = node;
              if(node!=null)
                node.setDisposed();
            }
            if(CoreUtils.DEBUG_LEVEL>7)
              RLogger.logDebug("TreeDB beforeDelete fin on "+ResManager.getManager().getVisibleName(TreeDB.this) );
          }
          finally{
            stopChangeRow();
          }
          
        }
        @Override
        public boolean changeAttributes(UUID uuid, ChangesSet<Attribute> cs, byte flag){
          if(cs.getNew(TreeNode.ATTR_INDEX_NOT_MANUAL)!=null){
            getCMM().getNewIndexesManipulator().addNewIndex(TreeDB.this, uuid);
          }else
            if(cs.getOld(TreeNode.ATTR_INDEX_NOT_MANUAL)!=null)
              getCMM().getNewIndexesManipulator().removeNewIndex(TreeDB.this, uuid);
          TreeNode curNode = getNode(uuid);
          if(curNode == null){
            return false;//TODO mb true
          }
          if(curNode.isDisposed())
            return false;
          curNode.attrs().commitChanges(cs, true, flag);
          return true;
          //TODO call commit?
        }
        /** Reaction on element change */
        @Override
        public void moved(
            UUID nodeUUID, UUID oldParentUUId, UUID newParentUUId, Moment m, byte flag) {
          TreeNode curNode = getCachedNodeOrVirt(nodeUUID);
          if (curNode == null) {
            TreeDB db = TreeDB.this;
            if (db.isVirtual) db = Instantiator.getRootTree(db);
            curNode = db.getCachedNodeOrVirt(nodeUUID);
          }
          if (curNode != null) {
            IStorageDeltaListener delta =
                getProperListener(nodeUUID, curNode.attrs().getActiveAttributeDefs());
            if (delta != null) {
              if (delta != this) {
                delta.moved(nodeUUID, oldParentUUId, newParentUUId, m, flag);
                return;
              }
            } else return;
          }else
            throw new RuntimeException("Moved node not found! "+nodeUUID);
          startChangeRow();
          try{
            TreeNode oldParent = getNode(oldParentUUId);
            TreeNode newParent = getNode(newParentUUId);
            if (m == Moment.BEFORE) {
              if(!OFlag.isNoUpdate(flag)) {
                List<ITreeChangeListener> list = getListeners();
                for (ITreeChangeListener l : list) 
                  l.moved(
                    curNode,
                    oldParent == null ? curNode.getWorkParent() : oldParent,
                    newParent == null ? curNode.getWorkParent() : newParent,
                    m, flag);
              }
              boolean changes = false;
              if (oldParent != null) {
                oldParent.removeChildUUID(nodeUUID, flag);
                curNode.attrs().getAttributeValue(TreeNode.ATTR_PARENT);//to load it for undo-redo and checks
                curNode.parent = null;//temp
                for (String s : oldParent.attrs().getLocalAttributeKeys()) { 
                  // all
                  Attribute vars = oldParent.attrs().getLocalAttribute(s);
                  if (vars != null) {
                    if(vars.isVirtual()) {
                      curNode.attrs().removeAttribute(s);
                      changes = true;
                    }else {
                      Attribute a = curNode.attrs().getAttribute(s);
                      if (a != null && a.getOrigin().equals(vars.getOrigin()))
                        if (a.getAvailability() != AttributeAvailability.LOCAL) {
                          curNode.attrs().removeAttribute(s);
                          changes = true;
                        }
                    }
                  }
                }
                curNode.getVS().setParentVersion(Type.ATTRIBUTES, Version.getMinusOne());
                curNode.getVS().setParentVersion(Type.STRUCT, Version.getMinusOne());
                changes = true;
              }
              if (changes)
                // curNode.parent = null;
                curNode.commitAttributes();
              // if(AttributeStorage.isMarkedUUIDs(TreeDB.this,nodeUUID)){
              // AttributeStorage.removeMark(TreeDB.this, nodeUUID);
              // curNode.save
              // }
            } else {
              boolean changes = false;
              if (newParent != null) { // TODO change par on null
                // RLogger.logInfo("null par");
                TreeNode par = getNode(oldParentUUId);
                if (par != null)
                  for (UUID chi : par.getActiveWorkChildrenUUIDs()) {
                    TreeNode child = getNode(chi);
                    if (child != null
                        && !child.getUUId().equals(curNode.getUUId())
                        && isSameClone(child, curNode)) {
                      //beforeDeleted(chi, true);
                      //deleted(chi, Moment.AFTER, true);
                      RLogger.logError("implement "+CoreUtils.getShortTrace());
                    }
                  }
                curNode.parent = newParentUUId;
                try{
                  newParent.addChildUUID(nodeUUID, flag);
                }catch(Exception e){
                  newParentUUId = oldParentUUId;
                  newParent = oldParent;
                  if(newParent!=null)
                    newParent.addChildUUID(nodeUUID, flag);
                  RLogger.logError(e);
                }
                if(newParent!=null)
                  for (String s : newParent.attrs().getLocalAttributeKeys()) // TODO
                  // all
                  {
                    Attribute list = newParent.attrs().getLocalAttribute(s);
                    if (list != null)
                      if (curNode.attrs().getAttribute(s) == null) {
    
                        Attribute a = list;
                        if (a.getAvailability() != AttributeAvailability.LOCAL) {
                          curNode.attrs().setAttribute(a.getDefinition(), true);
                          changes = true;
                        }
                      }
                  }
                  if(!OFlag.isNoUpdate(flag)){//TODO check
                    curNode.attrs().updateAttribute(TreeNode.ATTR_PARENT);
                    changes = true;//Parent changes
                  }else
                    RLogger.logInfo("Not internal move of node "+curNode.getUUId()); 
              }
              changes = changes || curNode.attrs().updateAttribute(TreeNode.ATTR_USERVISIBLEID);
              if (changes || curNode.attrs().hasChanges()) {
                curNode.commitAttributes();
              }
  
              if (!OFlag.isNoUpdate(flag)) {
                notifyExternalChange();
              }
              if (curNode.getType() == null) RLogger.logInfo("Null type!");
  
              if (VirtualNode.TYPE_NAME.equals(curNode.getType()))
                ((VirtualNode) curNode).markMoved();
              if(!OFlag.isNoUpdate(flag))
                curNode.fireNodeMoved(oldParent == null ? null : oldParent);
              if(!OFlag.isNoGuiUpdate(flag))
                curNode.fireGUINodeMoved(oldParent == null ? null : oldParent);
              if(!OFlag.isNoUpdate(flag)) {
                List<ITreeChangeListener> list = getListeners(); 
                for (ITreeChangeListener l : list) 
                  l.moved(
                    curNode,
                    oldParent == null ? curNode.getWorkParent() : oldParent,
                    newParent == null ? curNode.getWorkParent() : newParent,
                    m, flag);
                
              }
              if(!OFlag.isNoGuiUpdate(flag))
                fireNodeGUIMove(
                  curNode,
                  oldParent == null ? null : oldParent,
                  newParent == null ? null : newParent,
                  flag);

            }
          }
          catch(Exception er){
            er.printStackTrace();
            RLogger.logError(er);
          }
          finally{
            stopChangeRow();
          }
        }

        

        @Override
        public void resourceChanged(UUID uuid, String resName, Moment m, byte flag) {
          TreeNode node = getCachedNodeOrVirt(uuid, DEFAULT_DEPTH);
          if (node != null) {
            if(!OFlag.isNoUpdate(flag))
              node.fireNodeResourceChange(resName, m);
            if(!OFlag.isNoGuiUpdate(flag))
              node.fireGUINodeResourceChange(resName, m);
          }
        }

        @Override
        public void startChangeRow() {
          TreeDB.this.startChangeRow();
        }

        @Override
        public void stopChangeRow() {
          //if (TreeDB.this.isVirtual) Instantiator.getRootTree(TreeDB.this).stopChangeRow();
          TreeDB.this.stopChangeRow(); 

        }

        @Override
        public void versionChanged(
            UUID uuid, ChangesSet<Version> changes, Moment m, 
            byte flag) {
        }

        @Override
        public ITreeStorage operationStarted(String name, String opId) {
          TreeStateManager tsm = TreeStateManager.getOne(getTracker());
          if(!isDisposed && tsm.canStartTransaction(getLocalName(), tsm.getRevisionInfo(TreeDB.this))){
            operationId = opId;
            startTransaction(name, true);
            return getStorage();
          }
          return null;
        }

        @Override
        public void operationEnded(String opId) {
           if(operationId!=null&& opId.equals(operationId)){
             try{
               commit(false);
             }finally{
               operationId = null;
             }
           }
        }

        @Override
        public ITreeStorage getStorage() {
          return TreeDB.this.getStorage();
        }
      };
      public void handleTreeNodeChanges(
          TreeNode curNode, ChangesSet<Attribute> what, Moment m, byte flag) {

        if(CoreUtils.DEBUG_LEVEL>7)
          RLogger.logDebug("TreeDB changed on "+ResManager.getManager().getVisibleName(TreeDB.this) +" "+m.toString()); 
        //TreeNode curNode = getNode(uuid);
        //if (curNode == null) return;
        if(m == Moment.BEFORE && !OFlag.isNoUpdate(flag))
          fireNodeChange(curNode, what, Moment.BEFORE, flag);
        if(curNode.getTreeDB()!=this && m == Moment.BEFORE){//TODO remove if needed moment check
          curNode.getTreeDB().handleTreeNodeChanges(curNode,what,m,flag);
          return;
        }
        startChangeRow();
        try{
          if (m == Moment.BEFORE) {
            //TODO order?
             if(!what.isEmpty() && !OFlag.isNoGuiUpdate(flag))
              fireNodeGUIChange(curNode, what, flag, m);
          } else {
            if (what == null) {
              throw new RuntimeException("Empty changes set");
            }// else what = new HashSet<String>(what);
            if (curNode.getId() == null) {
              throw new RuntimeException("null id");
            }
            if (what.contains(TreeNode.ATTR_NAME))
              getLinkResolver().updateNameInCache(curNode.getUUId(), curNode.getName());
            if (!what.isEmpty()){
              what.getNewMap();
              curNode.reactAfterAttributeChange(what, flag);//TODO think
//              for (String s : what.getAll()) {
//                curNode.reactAfterAttributeChange(s, curNode.attrs().getAttribute(s));
//              }
              fireNodeChange(curNode, what, m, flag);
              curNode.fireNodeAttributeChange(what);
            }
            if (!what.isEmpty()) {
              if (!OFlag.isNoGuiUpdate(flag)){
                fireNodeGUIChange(curNode, what, flag, m);
                curNode.fireGUINodeAttributeChange(what);
              }
              if (!OFlag.isNoUpdate(flag)) 
                notifyExternalChange();
            }
            if(curNode.getTreeDB()!=this){
              curNode.getTreeDB().handleTreeNodeChanges(curNode,what,m,flag);
              return;
            }
          }
        }
        finally{
          stopChangeRow();
        }

        if(CoreUtils.DEBUG_LEVEL>7)
          RLogger.logDebug("TreeDB changed on "+ResManager.getManager().getVisibleName(TreeDB.this) +" "+m.toString()); 
      }
  private ConcurrentLinkedQueue<Future<?>> que = new ConcurrentLinkedQueue<>();
  public void addDelayed(Runnable object) {
    if (isVirtual && Instantiator.getRootTree(this) != this){
      Instantiator.getRootTree(this).addDelayed(object); 
      return;
    }
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> f = executor.submit(object);
    que.add(f);
  }

  public boolean isHidden(UUID uid) {
    Object attr = getAttributeValue(uid, VirtualNode.ATTR_HIDDEN_STATUS, false, false);
    if (attr != null && attr.toString().toLowerCase().equals("true")) return true;
    return false;
  }

//  public static ChangesSet<Attribute> addAttrsRec(TreeNode node, Set<String> what, ChangesSet<Attribute> chVis) {
//    return addAttrsRec(node, what, chVis, null);
//  }
  /**
   * Method to gather local deps from "what" set, update as and chVis. Adds order of execution to chVis 
   * @param as Attribute storage context
   * @param what set of names to be handled
   * @param chVis changes set to be extended
   * @param now set of new definitions to be used in attribute form mechanics
   */

  public static void applyChanges(AttributeStorage store, ChangesSet<Attribute> changes) {
    for(Entry<String, Attribute> s:changes.getNewMap().entrySet()) {
      Attribute anew = s.getValue();
      if(anew == null)
        store.actualRemoveFromAttributeList(s.getKey());
      else
        store.setAttribute(anew);
    }
  }
  public static ChangesSet<Attribute> addAttrsRec(AttributeStorage as, boolean withVirtual, boolean noPropogate, byte flag) {
//    AttributeStorage as = node.attrs();
    ChangesSet<Attribute> ret = new ChangesSet<>();
    try {
      //forming a set of definitions to be checked
      ChangesSet<AttributeDefinition> ch = as.getChanged();
      as.setChangingAttributes(ret);
      
      Set<String> handled = new TreeSet<>();
      List<String> order = new LinkedList<>();
      Map<String, List<String>> links = new TreeMap<>();
      Map<String, Set<String>> deps = new TreeMap<>();
            
      LinkedList<String>toh = new LinkedList<>(withVirtual?as.getAllAttributeKeys():as.getLocalAttributeKeys());
      
      toh.addAll(ch.getAll());
      
      //forms dependencies 
      Set<String> rev;
      Set<String> tohhan = new HashSet<>();
      while (!toh.isEmpty()) {
        String s = toh.removeFirst();
        tohhan.add(s);
        rev = as.getDependencies(s);
        deps.put(s, rev);
        for (String s2 : rev) {
          if(!tohhan.contains(s2))
            toh.addLast(s2);
          if (!links.containsKey(s2)){
            links.put(s2, new LinkedList<>());
          }
          links.get(s2).add(s);
        }
      }
      //forms set of attribute names to 
      LinkedList<String> sortedSet = new LinkedList<>(ch.getAll());
      sortedSet.sort(systemKeyPriority);
      Set<String> fullSet = new HashSet<>(sortedSet);
      LinkedList<String> openSet = new LinkedList<>(sortedSet);
      while (!openSet.isEmpty()) {
        //Iterator<String> next = openSet.iterator();
        String cs = openSet.removeFirst();
        //handled.add(cs);
        List<String> rel = links.get(cs);
        if (rel != null)
          for(String s:rel) 
            if(!fullSet.contains(s)){
              fullSet.add(s);
              openSet.addLast(s);
              sortedSet.addLast(s);
            }
      }
      
      openSet = sortedSet;
      String chosen = null;
      String cand;
      Attribute nw, ow;
      while (!openSet.isEmpty()) {
        Iterator<String> next = openSet.iterator();
        chosen = null;
        cand = null;
        while(next.hasNext() && chosen == null){
          cand = next.next();
          boolean f = true;
          Set<String> candDeps = deps.get(cand);
          if(candDeps == null)
            candDeps = as.getDependencies(cand); 
          for(String s:candDeps)
            if(fullSet.contains(s)&&!handled.contains(s)){
              f = false;
              break;
            }
          if(f){
            chosen = cand;
            next.remove();
          }
        }
        if(chosen != null){
          String cs = chosen;
          if(order.contains(cs))
            order.remove(cs);
          order.add(cs);
          handled.add(cs);
          AttributeDefinition a = ch.contains(cs)?ch.getNew(cs):as.getWorkAttributeDefinition(cs);
          a = as.getNode().alterAttributeBeforeChange(cs, a!=null?a:null);
          
          nw = null;
          ow = as.getLocalAttribute(cs);
          if(a != null){
            try{
              nw = a.toAttribute(as);
            }catch(Exception e){
              RLogger.logError(e);
              throw e;
            }
          }
          try {
            ret.addSomeChange(cs, ow, nw);
          } catch (OrderWasAlreadySetException e) {
            RLogger.logError(e);
          }
          List<String> rel = links.get(cs);
          if (rel != null)
            for(String s:rel) 
              if(!handled.contains(s)){
                openSet.add(s);
              }
        }else
          break;
      }
    
      try {
        ret.setOrder(order);
      } catch (OrderWasAlreadySetException e) {
        RLogger.logError(e);
      }
      if(!noPropogate)
        clearOldImages(as.getNode(), new HashSet<>(order), ret, flag);
      as.clearChanges();
      if(!openSet.isEmpty())
        RLogger.logInfo("uses cannot be resolved for "+as.getNode().getUUId());
      return ret;
    }finally {
      as.setChangingAttributes(null);
    }
  }
  private static void clearOldImages(TreeNode node, Set<String> atrs, ChangesSet<Attribute> ret, byte flag) {
    Set<String> res = node.listResources(),
                oldres = new HashSet<>();
    for(String s:res)
      for(String s2:atrs)
        if(s.startsWith(s2+"/"))
         oldres.add(s);
    Set<String> rem = new HashSet<>();
    Set<String> imgnow = new HashSet<>();
    
    for(String s:atrs)
    {
      Attribute atr = ret.getNew(s);
      AttributeDefinition a = atr!=null?atr.getDefinition():null;
      if(a == null || !a.getValueProvider().getType().equals(HtmlValueProvider.TYPE))
        rem.add(s);
      if(a!=null && a.getValueProvider().getType().equals(HtmlValueProvider.TYPE)){
        HtmlValueProvider hvp = (HtmlValueProvider)a.getValueProvider();
        for(String rs:hvp.gatherResNames())
          imgnow.add(s+"/"+rs);
      }
    }
    for(String s:oldres)
    {
      if(!imgnow.contains(s))
        node.deleteResource(s, flag);
    }
    
  }
  public Object getAttributeValue(UUID where, String key, Object object, boolean forcelocal) {
    // TODO check
    TreeNode node = getCachedNodeOrVirt(where);
    if (node != null)
      return node.getTreeDB()
          .getAttributeValue(
              where,
              forcelocal ? node.attrs().getAttribute(key) : node.attrs().getAttribute(key),
              object,
              forcelocal);
    Map<String, AttributeDefinition> atrs = getStorage().getAttributes(where);
    return AttributeStorage.getAttributeDefValue(this, atrs, where, key);
    // if(node!=null)
    // return getAttributeValue(forcelocal?
    // nde.attrs().getLocalAttribute(key,where):
    // nde.attrs().getLocalAttribute(key), object,forcelocal);
    // return object;
  }

  protected Object getAttributeValue(
      UUID where, Attribute next, Object object, boolean forcelocal) {
    if (next == null) return object;
    Object val = next.getValue();
    if (val == null)
      if (where.equals(next.getOrigin())) return null;
      else return getAttributeValue(next.getOrigin(), next.getKey(), object, forcelocal);
    return val;
  }

  protected Object getAttributeValue(
      UUID where, AttributeDefinition next, Object object, boolean forcelocal) {
    if (next == null || next.getRawValue() == null) return object;
    Object val = next.getRawValue().getValue();
    if (val == null)
      if (where.equals(next.getOrigin())) return null;
      else return getAttributeValue(next.getOrigin(), next.getKey(), object, forcelocal);
    return val;
  }

  protected Map<String, AttributeDefinition> getAttributes(UUID uid) {
    AttributeStorage as = AttributeStorage.getCachedStorage(this, uid);
    if (as != null) return as.getActiveAttributeDefs();
    TreeNode cache = getCachedNodeOrVirt(uid);
    if (cache != null) return cache.attrs().getActiveAttributeDefs();
    if (isVirtual) return Instantiator.getRootTree(this).getStorage().getAttributes(uid);
    return getStorage().getAttributes(uid);
  }

//  /**
//   * Method to replace node attributes by stored values
//   *
//   * @param node element to replace attributes on
//   */
//  public void updateNodeAttributes(TreeNode node,boolean first) {
//    Map<String, Attribute> stA = storage.getAttributes(node.getUUId());
//    updateNodeAttributes(node, stA,first);
//  }

  public void initNodeAttributes(final TreeNode node, Map<String, AttributeDefinition> newAttributes, boolean noPropogate, byte flag) {
    Map<String, AttributeDefinition> now = newAttributes; // TODO check
    updateParentCloneParentAttributeDefs(
      node.getUUId(), node.attrs(), null, node.getWorkParentUUID(), now);
    node.attrs().setChanged(new ChangesSet<>(new HashSet<>(now.keySet()),
        null,null,null,now,null));
    ChangesSet<Attribute> changes = addAttrsRec(node.attrs(), false, noPropogate, flag);
    TreeDB.applyChanges(node.attrs(), changes);
    //TODO look on it?
    //todo mb move
    if(!noPropogate)
      for(IAttributeChangePropagator acp:API.getAttributeChangePropogators(node)){
        node.addAPropogator(acp);
        acp.propagatorInited(node);
      }
    changes = addAttrsRec(node.attrs(), false, noPropogate, flag);
    TreeDB.applyChanges(node.attrs(), changes);
    
    node.attrs().clearChanges();
    //node.attrs().commitChanges(cs, now, false);
  }

  /**
   * Method to get uuid generator
   *
   * @return uuid generator
   */
  public IUUIDGenerator getUuidGenerator() {
    return uuidGenerator;
  }

  /**
   * Get current data storage. It may be related to one of possible storage mechanisms. Most
   * frequently used are Resource storage based on eclipse mechanisms and Transaction storage -
   * temporary storage during transactions. One more variant is TreeInstancer that store data during
   * report generation
   *
   * @return current data storage
   */
  public ITreeStorage getStorage() {
    return storage;
  }

  public TreeInstancer getVStorage() {
    if (!isVirtual) return Instantiator.getVirtualStorage(this, Instantiator.DEFAULT_INSTANCE_NAME);
    if (storage instanceof TreeInstancer) return (TreeInstancer) storage;
    if (storage instanceof TransactionStorage)
      return (TreeInstancer) ((TransactionStorage) storage).getVStorage();
    return null;
  }

  private boolean defaultTypesContainsKey(String typeName) {
    if (isVirtual) {
      TreeDB db = Instantiator.getRootTree(this);
      if (db != null && db != this) return db.defaultTypesContainsKey(typeName);
    }
    return defaultTypes.containsKey(typeName);
  }

  private NodeType defaultTypesGet(String typeName) {
    if (isVirtual) {
      TreeDB db = Instantiator.getRootTree(this);
      if (db != null && db != this) return db.defaultTypesGet(typeName);
    }
    return defaultTypes.get(typeName);
  }

  private TreeNode createModelNode(
      UUID uuid,
      UUID puuid,
      UUID cloneParent,
      String id,
      String typeName,
      Map<String, AttributeDefinition> attributes) {
    attributes = new HashMap<>(attributes);
    TreeNode result = null;
    if (defaultTypesContainsKey(typeName)) {
      NodeType nt = defaultTypesGet(typeName);
      try {
        Class<?>[] treeNodeArgs = new Class[] {TreeDB.class, UUID.class};
        result = nt.getTypeClass().getConstructor(treeNodeArgs).newInstance(TreeDB.this, uuid);
        result.setType(typeName);
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
    if (result == null) {
      result = new TreeNode(this, uuid);
      result.setType(typeName);
    }
    result.setId(id, true);
    result.cloneParent = cloneParent;
    result.parent = puuid;
    if (cloneParent == null) {

      Object cp = null;
      AttributeDefinition cpa = attributes.get(TreeNode.ATTR_CLONE_PARENT);
      if (cpa != null) cp = cpa.getRawValue().getValue();
      // getAttributeValue(uuid, TreeInstancer.ATTR_CLONE_PARENT, null,
      // true);
      if (cp != null) result.cloneParent = UUID.fromString(cp.toString());
    }
    // TODO check
    cache.put(uuid, result);
    // if(!isVirtual)
    // VirtualNode.getVirtualTreeDB(this).cache.put(uuid, result);
    Attribute parentRoot = result.attrs().getAttribute(TreeNode.ATTR_VIRT_STATUS);
    if (parentRoot != null && parentRoot.getValue() != null)
      result.vroot = parentRoot.getDefinition().getRawValue().getValue().toString();
    initNodeAttributes(result, attributes, false, OFlag.NONE);
    return result;
  }

  public void updateNodeChildren(TreeNode result) {
    updateNodeChildren(result, false);
  }

  public static void updateNodeChildren(TreeNode result, boolean init) {
    if (result == null) return;
    TreeDB db = Instantiator.getRootTree(result.getTreeDB(), 3);
    if ((!db.storage.canLoad(result.getUUId()) || result.isVirtual() && !db.isVirtual)) {
      if (!db.isVirtual && result.isVirtual()) {
        String vroot = result.getVRoot();
        if (vroot != null) db = Instantiator.getExistVirtualTreeDB(db, vroot);
      }
    }
    db.updateNodeChildren(result, db.storage.getChildren(result.getUUId()), init);
  }

//  public void updateShownNodeChildren(TreeNode result, boolean init) {
//    // if(result.getTreeDB()!=this)
//    // RLogger.logInfo("wrong db!");
//    // Collection<UUID> uids = null;
//    // if(result.getShownChildrenUUIDs()!=null)
//    // uids = new HashSet<UUID>(result.getShownChildrenUUIDs());
//    // int ocnt = 0;
//    // if(uids!=null)
//    // ocnt = uids.size();
//    // List<UUID> ids = null;
//    // //uids.removeAll(ids);
//    //
//    result.clearShownChildren();
//    if (!init) {
//      //TODO struct update
//      //fireNodeGUIChange(result, TreeNode.formIdEvent(), Collections.emptyMap(), true);
//      //result.fireGUINodeAttributeChange(TreeNode.IdEvent);
//    }
//  }

  private void updateNodeChildren(TreeNode result, Collection<UUID> children, boolean init) {
    result.getChildrenRelatedVirtuals();
    result.setChildrenUUIDs(new ArrayList<>(children), init);
    result.updateLocalAttribute(TreeNode.ATTR_CHILDREN_UUIDS);//if called on init - may cause recursion
    if(!init)
      result.commitAttributes();
    else
      result.applyLocalAttributes(OFlag.NONE, false);//TODO with virt?
    
    if (!init) {
      // result.getVS().incVersion(Type.STRUCT);
      // fireNodeChange(result,new HashSet<String>(){{add("_%id");}},
      // true);
      // fireNodeGUIChange(result,new HashSet<String>(){{add("_%id");}},
      // true);
    }
    for(UUID node:Instantiator.getClones(result.getUUId(), this)) {
      TreeNode clone = getCachedNodeOrVirt(node);
      if(clone!=null)
        VirtualNode.updateChildrenOfClone(clone);
    }
  }

  public TreeNode createFromStorage(
      UUID uuid, String typeName, Map<String, AttributeDefinition> attributes) {
    String id;
    UUID puuid;
    long l = System.currentTimeMillis();
    if ("DBRoot".equals(typeName)) {
      id = "/";
      puuid = null;
    } else {
      id = storage.getNodeId(uuid);
      puuid = storage.getParent(uuid);
    }
    if (id == null) {
      throw new RuntimeException("Id is null for" + uuid);
    }
    // if(isVirtual&&storage instanceof TreeInstancer)
    // ((TreeInstancer)storage).createIfNeeded(uuid);
    UUID cloneP = null;
    if (attributes.containsKey(TreeNode.ATTR_CLONE_PARENT)
        && attributes.get(TreeNode.ATTR_CLONE_PARENT) != null) {
      AttributeDefinition o = attributes.get(TreeNode.ATTR_CLONE_PARENT);
      Object val = o.getRawValue().getValue();
      if (val == null){
        val = getAttributeValue(uuid, o, null, false);
      }
      if (val != null) 
        cloneP = UUID.fromString(val.toString());
    }
    if (CoreUtils.DEBUG_LEVEL > 7) {
      RLogger.logDebug("\t get cp " + (System.currentTimeMillis() - l));
      l = System.currentTimeMillis();
    }

    // if(isVirtual)
    // cloneP = VirtualNode.getVirtualStorage(this).getCloneParent(uuid);
    TreeNode result = createModelNode(uuid, puuid, cloneP, id, typeName, attributes);
    if (result!=null && result.getId() == null) {
      throw new RuntimeException("Id is null for" + uuid);
    }
    if (CoreUtils.DEBUG_LEVEL > 7) {
      RLogger.logDebug("\t create model " + (System.currentTimeMillis() - l));
      l = System.currentTimeMillis();
    }
    Collection<UUID> children = storage.getChildren(uuid);
    if (CoreUtils.DEBUG_LEVEL > 7) {
      RLogger.logDebug("\t get children " + (System.currentTimeMillis() - l));
      l = System.currentTimeMillis();
    }
    updateNodeChildren(result, children, true);
    if (CoreUtils.DEBUG_LEVEL > 7) {
      RLogger.logDebug("\t update children " + (System.currentTimeMillis() - l));
      l = System.currentTimeMillis();
    }
    if (result == null) RLogger.logInfo("null result for call create from  storage on "+uuid);
    else {
    }
    if (result!=null && result.getId().equals("/")) {
      assert result.parent == null;
      root = result;
    }
    return result;
  }

  /**
   * Method to fire element change event. It will be translated to all subscribed listeners.
   *
   * @param curNode changed element
   * @param m 
   * @param internal true if event appears from inner mechanics
   */
  public void fireNodeChange(
      TreeNode curNode, ChangesSet<Attribute> params, Moment m, byte flag) {
    if (isVirtual && curNode != null) {
      TreeDB db = Instantiator.getRootTree(this);
      if (db != null && db != this) db.fireNodeChange(curNode, params, m, flag);
    } else {
      List<ITreeChangeListener> list = getListeners();
      if(list!=null)
        for (ITreeChangeListener l : list) 
          if (l != null) // Oo
            l.changed(curNode, params, m, flag);
    }
  }

  /**
   * String for representing of Node's types filter in form of list of string names of types. For
   * ex. {TestPurpose.TYPE_NAME}"
   *
   * @return
   */
  public List<String> getLinkToTypeFilter(String linktype) {
    // TODO TreeLogic methods
    if (linktype.equals("TestStepLinks")) {
      ArrayList<String> a = new ArrayList<>();
      a.add(TestPurpose.TYPE_NAME);
      return a;
    }
    return typeFilter;
  }

  public List<String> getRelatedLinkTo() {
    ArrayList<String> a = new ArrayList<>();
    a.add("TestStepLinks");
    return a;
  }

  public List<String> getLinkToTypeFilter() {
    // TODO TreeLogic methods
    List<String> types = new ArrayList<>(), lt = getRelatedLinkTo();
    for (String s : lt) types.addAll(getLinkToTypeFilter(s));
    return types;
  }

  public SortingSettings getSortingSettings() {
    if (isVirtual) {
      TreeDB db = Instantiator.getBaseTree(this);
      if (db != null && db != this) return db.getSortingSettings();
      return null;
    }
    return loadSettings(getRootNode());
  }

  public static SortingSettings loadSettings(TreeNode node) {
    return SortingSettings.load(node);
  }

  public void fireNodeChildrenOrderChange(TreeNode shownParent, byte flag) {
    fireNodeChildrenOrderChange(shownParent,  Moment.AFTER, true, flag);
  }
  public void fireNodeChildrenOrderChange(TreeNode shownParent, Moment m,boolean clear, byte flag) {
//    if(clear)
//      shownParent.clearShownChildren();
    if(OFlag.isNoGuiUpdate(flag))
      return;
    if (activeChangesRowsCount == 0)
      for (ITreeChangeListener l : getGUIListeners()) {
        l.childrenOrderChanged(shownParent, flag);
      }
    else
      addGuiChange(
          new TreeNodeChangeEvent(TreeNodeChangeEventType.CHILDREN_ORDER_CHANGE, m, shownParent, flag));
  }
  public void fireNodeGUIChange(
      TreeNode curNode, ChangesSet<Attribute> cs, byte flag, Moment m) {
    if(OFlag.isNoGuiUpdate(flag))
      return;
    if (activeChangesRowsCount == 0)
      for (ITreeChangeListener l : getGUIListeners()) {
        l.changed(curNode, cs, m, flag);
      }
    else
     addGuiChange(
        new TreeNodeChangeEvent(TreeNodeChangeEventType.CHANGE, m, curNode, flag, new ChangesSet<>(cs)));
  }

  public void fireNodeGUIMove(TreeNode curNode, TreeNode from, TreeNode to, byte flag) {
    if (activeChangesRowsCount == 0)
      for (ITreeChangeListener l : getGUIListeners()) {
        l.moved(curNode, from, to, Moment.AFTER, flag);
      }
    else {
      if(!OFlag.isNoGuiUpdate(flag))
        addGuiChange(
          new TreeNodeChangeEvent(TreeNodeChangeEventType.MOVE, Moment.AFTER, curNode, flag, from, to));
    }
  }

  private void addGuiChange(TreeNodeChangeEvent treeNodeChangeEvent) {
    wl.lock();
    try {
      guichanges.add(treeNodeChangeEvent);
    }finally {
      wl.unlock();
    }
  }

  public IStorageDeltaListener getDeltaTarget() {
    return storageDeltaListener;
  }

  public LinkResolver getLinkResolver() {
    return getCMM().getLinkManipulator();
  }

  public boolean isLoaded(UUID uid) {
    if (cache.containsKey(uid)) return true;
    if (isVirtual) {
      TreeDB base = Instantiator.getBaseTree(this);
      if (base == null)
        return false;
      if (base != this) return base.isLoaded(uid);
      return false;
    } else {
      for (TreeDB vdb : Instantiator.getVirtualTreeDBs(this))
        if (vdb.cache.containsKey(uid)) return true;
    }
    return false;
  }

  public static String getDefValueOfDefReferenceName(){
    return "elaborates";
  }
  public String getDefReferenceName() {
    if (isVirtual) {
      TreeDB db = Instantiator.getBaseTree(this);
      if (db != null && db != this) return db.getDefReferenceName();
      return null;
    }
    if (getRootNode() == null || root.attrs() == null) RLogger.logInfo("null root or attrs");
    Attribute a = root.attrs().getAttribute(DEFAULT_REFERENCE_NAME);
    if (a != null) return a.getValue().toString();
    return getDefValueOfDefReferenceName();
  }

  public List<ITransactionListener> getTransactionListeners() {
    TreeDB root = this;
    if(root.isVirtual)
      root = Instantiator.getRootTree(this);
    if (root.equals(this)) return transactionListeners;
    else return root.getTransactionListeners();
  }

  public String getQualifiedId(UUID origin) {
    if (origin == null || !getStorage().canLoad(origin)) return "";
    String id = getStorage().getNodeId(origin);
    if (id.equals("/")) return "";
    return getQualifiedId(getStorage().getParent(origin)) + "/" + id;
  }

  public String getFullName(String targetQID) {
    try {
      return getFullName(getNode(targetQID, Path.QID | Path.UUID));
    } catch (Exception e) {

    }
    return null;
  }

  public String getFullName(TreeNode node) {
    return getFullName(node, null, false);
  }

  public String getFullName(TreeNode node, Integer mode, boolean inner) {
    if (node == null) return null;
    if(mode == null)
      mode = getFullNameMode();
    switch(mode) {
      case 0:
        return Requirement.getFullName(node, this, inner);
      case 1:
        return node.getUserVisibleName(inner);
      case 2:
        return node.getQualifiedId();
      case 3:
        String ret = node.getExternalForeignID();
        if(ret == null)
          return node.getUserVisibleName(inner);
        return ret;
    }
    return null;
  }

  public IStorageDeltaListener getNullDeltaListener() {
    return nullDeltaListener;
  }

  public Collection<Attribute> getEnumDefinitons() {
    HashSet<Attribute> ret = new HashSet<>();
    for (Attribute a : root.attrs()) {
      if (a != null && a.getType().equals(AttributeType.ENUM_DEFINITION)) ret.add(a);
    }
    return ret;
  }
 
//  public IMark[] getCheckerProblems(TreeNode node) {
////    IMark[] ret =  getChprob().getOrDefault(node.getUUId(), EMPTY);
////    if(ret == null)
////      return EMPTY;
//    return ret;
//  }

  public int getLevelOfAbstraction() {
    return loa;
  }
  
  public int getChangeRowCount() {
    return activeChangesRowsCount;
  }

  public UUID getCloneParent(UUID node) {
    Object val = getAttributeValue(node, TreeNode.ATTR_CLONE_PARENT, null, false);
    if (val != null) return UUID.fromString(val.toString());
    return null;
  }

  public void setSortingMethod(SortingSettings sortingSettings, boolean save) {
    TreeDB db = this;
    if(db.isVirtual)
      db = Instantiator.getRootTree(db);
    TreeNode root = db.getRootNode();
    root.attrs().setConstAttribute(SortingSettings.SORT_METHOD, sortingSettings.getMethod().toString(),Flag.HIDDEN);
    root.attrs().setConstAttribute(SortingSettings.SORT_SEP_TYPES, sortingSettings.getSeparateByTypesFlag(),Flag.HIDDEN);
    if (save) root.commitAttributes();
  }

  public static void updateVisablility(TreeDB db, List<Object> nodes, boolean notcheck) {
    if (nodes == null || nodes.size() == 0) return;
    new ArrayList<>();
    // TODO look into predicate updates mechanics
    // may be incorrect
    Iterator<Object> it = nodes.iterator();
    while(it.hasNext()) {
      Object o = it.next();
      TreeNode node = null;
      try {
        if(o instanceof TreeNode)
          node = (TreeNode) o;
        else
          node = db.getNode((UUID)o, DEFAULT_DEPTH, notcheck);
      
        if (node == null
            || node.attrs()
                .getBooleanAttributeValue(TreeNode.ATTR_VIRT_REMOVED, false) 
            || node.isDisposed()
            || node.isHidden()){
          it.remove();
          continue;
        }
             
      } catch (RuntimeException e) {
        RLogger.logError(e);
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
  }

  public void clearCaches(boolean delete,boolean forceCheck) {
    try {
      tcm.getSettingsStore();
    }catch(RuntimeException e) {
      return;
    }
    try{
      clearWarnings();
      getCheckerContainer().stopOldThread();
      TreeCacheManipulatorsManager man = getCMM();
      man.clearCaches(this, man.getSettingsStore());
      man.clearCMMCaches();
      Instantiator.clearCache(this);
      getStorage().clearCaches(delete);
    }
    catch(Exception r){
      r.printStackTrace();
      throw r;
    }
    finally{
    }
    //setDisposed();
    if(forceCheck)
      getCheckerContainer().addAllLoadedToQ();
  }

  private TreeNode clearNodeCache(UUID uuId, UUID par, int deep) {
    if (deep <= 0) return null;
    getLinkResolver().removeLocalCacheForNode(this, uuId);
    tracker.getCPLinkResolver(this).removeCPCacheForNode(this, getLocalName(),getProjectName(), uuId);
    Set<String> rem = attrRootRevCache.remove(uuId);
    if (rem != null)
      for (String s : rem) {
        attrRootCache.get(s).remove(uuId);
        if (attrRootCache.get(s).isEmpty()) attrRootCache.remove(s);
      }
//    if (par != null) {
//      TreeNode c = getCachedNodeOrVirt(par);
//      if (c != null) c.clearShownChildren();
//    }//TODO rewrite
    TreeNode node = getCachedNodeOrVirt(uuId);
    cache.remove(uuId);
    if (isVirtual) {
      TreeDB db = Instantiator.getBaseTree(this);
      if (db != null) db.clearNodeCache(uuId, par, deep - 1);
    }
    for (TreeDB db : Instantiator.getVirtualTreeDBs(this)) db.clearNodeCache(uuId, par, deep - 1);
    if(node!=null)
    {
      node.clearChecked();
      getCMM().clearNodeCache(this, getCMM().getSettingsStore(), node);
      if (node.getType().equals(Document.TYPE_NAME)) ((Document) node).clearCache();
      if (node instanceof Requirement && node.attrs().getAttribute(Requirement.ATTR_LOCATIONS)!=null) ((Requirement)node).updateLocations((Requirement)node);
      if (node.getType().equals(Location.TYPE_NAME)) ((Location) node).resetRelated(node);
      return node;
    }
    return null;
  }

//  public Map<UUID, TreeNode> getCache() {
//    return cache;
//  }

  public void close() {
    // clearAllCaches(false);
    getCMM().getClose();
  }

  @Override
  public String toString() {
    return getVisibleName() + " [" + getLocalName() + "]"+ "@" + Integer.toHexString(hashCode());
  }

  public TermsManipulator getTermsStorage() {
    return getCMM().getTermsManipulator();
  }

  public TreeNode getOrCreateTypes() {
    TreeNode node = getNode("/Types");
    if(node == null)
      node = createChildNode(getRootNodeUUID(), "Types", "Node");
    return node;
  }

//  public void clearCheckProblems() {
//    chprob.clear();
//  }
// 
//  public void clearCheckProblems(TreeNode node) {
//    chprob.remove(node.getUUId());
//  }

  public IResTracker getTracker() {
    return tracker;
  }
  public void setTracker(IResTracker nw) {
    tracker = nw;
    tcm = null;
  }

  public void addWarnings(TreeNode node, List<IMark> warns) {
    if(warns == null || warns.isEmpty())
      return;
    ConcurrentLinkedQueue<IMark>marks = chwarns.get(node.getUUId());
    if(marks == null)
      marks = new ConcurrentLinkedQueue<>();
    if(warns.contains(null)){
      RLogger.logError("Warnings contains null value");
      RLogger.logError(CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
      warns.remove(null);
    }
    if(marks.addAll(warns)){
      ChangesSet<Attribute> changes = new ChangesSet<>();
      try {
        changes.addSomeChange(ReferenceOutdateMark.WARNING_KEY, null, 
            new Attribute(new AttributeDefinition(node.getUUId(), 
            ReferenceOutdateMark.WARNING_KEY, new ConstValueProvider(AttributeType.LIST, 
                new LinkedList<>(marks)), Flag.HIDDEN|Flag.VIRTUAL), node.attrs()));
        LinkedList<String> ord = new LinkedList<>();
        if(ord.contains(ReferenceOutdateMark.WARNING_KEY))
          ord.remove(ReferenceOutdateMark.WARNING_KEY);
        ord.add(ReferenceOutdateMark.WARNING_KEY);
        changes.setOrder(ord);
      } catch (OrderWasAlreadySetException e) {
        RLogger.logError(e);
      }
      chwarns.put(node.getUUId(), marks);
      node.fireGUINodeAttributeChange(changes);
    }
  }

  public void removeWarns(TreeNode node, IMark ... localwarns) {
    if(localwarns == null)
      return;
    ConcurrentLinkedQueue<IMark> marks = chwarns.get(node.getUUId());
    Collection<IMark> col = Arrays.asList(localwarns);
    if(marks!=null){
      marks.removeAll(col);
      ChangesSet<Attribute> changes = new ChangesSet<>();
      try {
        changes.addSomeChange(ReferenceOutdateMark.WARNING_KEY, null, 
            new Attribute(new AttributeDefinition(node.getUUId(), 
            ReferenceOutdateMark.WARNING_KEY, new ConstValueProvider(AttributeType.LIST, 
                new LinkedList<>(marks)), Flag.HIDDEN|Flag.VIRTUAL), node.attrs()));
        node.fireGUINodeAttributeChange(changes);
      } catch (OrderWasAlreadySetException e) {
        RLogger.logError(e);
      }
    }
  }
  public void clearWarnings(){
    chwarns.clear();
  }
  public ConcurrentLinkedQueue<IMark> getWarnings(TreeNode node) {
    ConcurrentLinkedQueue<IMark> marks = chwarns.get(node.getUUId());
    if(marks!=null)
      return marks;
    return null;
  }

  @Override
  public CacheStorage getCacheStorage(TreeDB dbfrom) {
    return getCacheStorage();
  }

  public NodeCache getCache() {
    return cache;
  }

  @Override
  public CacheStorage getCPCacheStorage(TreeDB dbfrom) {
    return getTracker().getCPCacheStorage(dbfrom);
  }


  private TreeCacheManipulatorsManager tcm = null;
  public RevisionInfo revision = null;
  private String localRevisionString = null;
  private boolean localRevisionInited = false;
  public String getLocalRevisionString() {
    if(localRevisionInited)
      return localRevisionString;
    if(revision == null)
      return null;
    localRevisionInited = true;
    localRevisionString = revision.getRevision(this);
    return localRevisionString;
  }
  public MultiPart getMultipartSettings(){
    if(mpSettings != null)
      return mpSettings;
    TreeDB base = Instantiator.getBaseTree(this);
    if(base!=null && base != this)
    {
      MultiPart mps = base.getMultipartSettings();
      if(mps!=null)
        return mps;
    }
    mpSettings = new MultiPart();
    return mpSettings;
  }
  @Override
  public MultiPart getMultiPart(TreeNode root, ReportUtils rgh, ReportGenerationContext context) {
    return getMultipartSettings();
  }
  public void initCaches(String lname,String pname, RevisionInfo bri, boolean now) {
    TreeStateManager tsm = TreeStateManager.getOne(getTracker());
    AtomicBoolean stopped = new AtomicBoolean(false);
    //RLogger.logInfo("init Caches for "+lname+" "+now +" "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
    try {
      //PRE stage inited on storage, now init storage cache full
      getStorage().initCaches(getStorage(), CacheInitializationStage.FULL, getMonitor());//TODO here?
    }catch(Exception e) {
      RLogger.logError(e);
      return;
    }
    RunnableWithMonitor rwp =  new RunnableWithMonitor() {
        @Override
        public void run(IProgressMonitor pm) {
          //EclipseProgressMonitor epm = EclipseProgressMonitor.from(pm);
          TreeDB db = TreeDB.this;
          LocationHelper.getLocation(db, false);
          List<Pair<Object,CacheManipulatorsManager>> cmans = new LinkedList<>();
          db.getTracker();
            try{
              if(isDisposed || pm.isCanceled())
                return;
              cmans.add(new Pair<>(getStorage(), getStorage().getCMM()));
              CacheStorage ccs = getCPCacheStorage(db);
              CacheStorage cs2 = getCacheStorage();
              CacheTransactionDescription b = cs2.startCacheTransaction("init caches on "+lname, CacheStorage.COMMIT_ALL, true);
              CacheTransactionDescription a = ccs==null?null:ccs.startForcedCacheTransaction("init cp caches on "+lname, CacheStorage.COMMIT_ALL, true);
              Lock wl = getStorage().writeLock(null);
              boolean wnl = true;
              try{
                cmans.add(new Pair<>(db, getCMM()));
                getCMM().initCM(db, pm);
                TrackerCacheManipulatorsManager tcsm = getTracker().getCMM(db);
                if(tcsm!=null) {
                  tcsm.initCM(tracker, pm);
                  cmans.add(new Pair<>(db, tcsm));
                }
                tsm.setState(lname, pname, db, bri, TreeState.LOADED_NO_CACHE, true);
                db.getLocalName();
                try {
                  if(pm.isCanceled())
                    return;
                  Instantiator.loadDBs(db, pm);
                  if(pm.isCanceled())
                    return;
                  getCMM().initCache(db,getCMM().getSettingsStore(), CacheInitializationStage.FULL, pm);
                  if(tcsm!=null)
                    tcsm.initCache(db, tcsm.getSettingsStore(), CacheInitializationStage.FULL, pm);//both stages
                  Instantiator.removeTempInstances(db, pm);
                  wl.unlock();
                  wnl = false;
//                  if(a!=null && a.error == null) {
//                    ccs.commit(a);
//                  }
//                  if(b!=null && b.error == null) {
//                    cs2.commit(b);
//                  }
                }finally {
                  tsm.markCachesInited(getTracker(), lname, bri);
                }
              }
              finally{
                if(wnl)
                  wl.unlock();
                if(ccs!=null && a != null && a.error == null)//TODO rollback?
                  ccs.commit(a);
                if(b != null && b.error == null)
                  cs2.commit(b);
              }
            }catch(Exception e) {
              if(!db.isDisposed && !stopped.get())
                RLogger.logError(e);
            }finally{
              if(pm.isCanceled())
              {
                for(Pair<Object,CacheManipulatorsManager> p:cmans) {
                  p.getSecond().dropParallelCacheInits(p.getFirst(), p.getSecond().getSettingsStore());
                }
              }
              if(!db.isDisposed && !stopped.get()) {
                getTracker().notifyListeners(Moment.BEFORE, lname, pname, db, TreeEvent.TREE_OPEN);
                TreeStateManager.getOne(getTracker()).setState(lname, pname, db, TreeState.LOADED);
                getTracker().notifyListeners(Moment.AFTER, lname, pname, db, TreeEvent.TREE_OPEN);
              }
              //if(markinited)
                //db.setIsLoaded();
              //else
               // TreeStateManager.getOne().setState(lname, TreeState.NOT_LOADED);
            }   
        }
    };
    if(!now) {
      TreeState[] states = new TreeState[] {TreeState.READY, TreeState.HALTED, TreeState.LOADED_NO_CACHE};
      tsm.addStateHook(lname, states, new IStateChangeHandler() {
        //TODO can be tricky, still not used for now
        @Override
        public boolean filterTreeDB(TreeDB db) {
          return db == TreeDB.this;
        }
        @Override
        public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
            TreeState oldState, TreeState state) {
          if(RevisionInfo.isSame(ri,bri) && (db==null || (db!=TreeDB.this) || state == TreeState.HALTED)) {
            stopped.set(true);
            JobManager.jm.haltJob(rwp);
          }
          tsm.removeStateHook(lname, states, this);
        }
      });
      JobManager.jm.startJob("Init caches for "+lname,rwp, false);
    }
    else{
      rwp.run(getMonitor());
    }
  }
  private String localcmmLock = null;
  protected String getCMMLockObject() {
    if(localcmmLock==null) {
      synchronized (this) {
        if(localcmmLock == null)
          localcmmLock = getSemiLocalName();
        if(localcmmLock == null)
          localcmmLock = this.toString();//fallback mode
      }
    }
    return localcmmLock;
  }
  public TreeCacheManipulatorsManager getCMM() {
    return getCMM(true);
  }
  public TreeCacheManipulatorsManager getCMM(boolean reinit) {
    synchronized(getCMMLockObject()) {
      if((tcm == null || tcm.isDisposed())&&reinit){
        if(tcm!=null)
          tcm.clearCMMCaches();
        tcm = new TreeCacheManipulatorsManager();
        tcm.initCM(this, storage.getMonitor());
      }
    }
    return tcm;
  }


  public void clearNodeCache(UUID uuId, UUID workParentUUID, boolean del) {
    //TODO use del info?
    clearNodeCache(uuId, workParentUUID, DEFAULT_DEPTH);
  }

  public void setLabelIsSimple(boolean b) {
    //boolean tr = startTransaction("Set 'Description in label' mode to "+b+" on "+getSemiResName());
    try{
      simpleLabelMode = b;
      if(b)
        root.attrs().removeAttribute(DescModeValueProvider.LABEL_IS_SIMPLE_PARAM);
      else
        root.attrs().setConstAttribute(DescModeValueProvider.LABEL_IS_SIMPLE_PARAM,
          b, Flag.HIDDEN);
      root.commitAttributes();
    }finally{
   //   if(tr)
   //     commit();
    }
  }

  public void setDisposed() {
    setDisposed(false);
  }
  public void setDisposed(boolean noStatusChange) {
    isDisposed = true;
    TreeStateManager tsm = TreeStateManager.getOne(getTracker());
    RevisionInfo ri = tsm.getRevisionInfo(this);
    if(!noStatusChange) {
      tsm.setDisposed(getLocalName(), getProjectName(), this, ri);
    }else {
//      if(tsm.getState(getLocalName(), ri)==TreeState.READY) {
//        tsm.setState(getLocalName(), this, ri, TreeState.DISPOSED);
//      }
    }
  }
  public boolean isBaseDisposed() {
    return isDisposed;
  }
  public boolean isDisposed() {
    TreeStateManager tsm = TreeStateManager.getOne(getTracker());
    return isBaseDisposed() || tsm!=null && tsm.isDisposed(getLocalName(), tsm.getRevisionInfo(this));
  }


  public boolean inTransaction() {
    return storage instanceof TransactionStorage;
  }


  public IProgressMonitor getMonitor() {
    return storage.getMonitor();
  }




}
