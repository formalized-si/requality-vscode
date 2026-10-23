package com.unitesk.requality.core.selection;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.tool.CoreUtils;
/**
 * Use scenario - all event sources needs to be registered on stard and removed on dispose
 * All should have set of active  
 * @author ruswizard
 *
 */
public class SelectionManager {
  private final static int SELECTION_CHANGED_TIMEOUT=250;
//TODO add locks
  private final static EventSource[] NO_SOURCES = new EventSource[0];
  public final static TreeNode[] NO_NODES = new TreeNode[0];
  private final static int LIMIT = 6;
  private final static SelectionManager sm = new SelectionManager();
  public static final byte MARKUP_FLAG = 1;
  private static long MAX_OP_ID=0;//id of selection change operation
  private boolean trackerInited = false;//TODO mb replace by multitrack system
  public enum SourcePriority{NO, PROPERTIES, SOURCE_CHANGE, EDITOR}
  private SourcePriority sourceChangePending = SourcePriority.NO;
  
  private long lastTimeout = -1;
  private FSId savedFSId = null;
  private SourcePriority lastPrior = null;
  
  private LinkedList<EventSource> activeSources = new LinkedList<EventSource>();
  private ConcurrentHashMap<EventSource, LinkedList<FSId>> allInstance = new ConcurrentHashMap<EventSource, LinkedList<FSId>>();
  private ConcurrentHashMap<EventSource, FSId[]> activeInstance = new ConcurrentHashMap<>();
  private ConcurrentHashMap<Long, FSId> instances = new ConcurrentHashMap<>();//TODO?
  private ConcurrentHashMap<Long, Boolean> instanceIsSource = new ConcurrentHashMap<>();
  
  private ConcurrentHashMap<Long, SelectionRecord> instanceStoredActiveSelection = new ConcurrentHashMap<>();
  private ConcurrentHashMap<Long, SelectionRecord> instanceVisibleActiveSelection = new ConcurrentHashMap<>();
  
  
  private ConcurrentHashMap<Long, FSId> instanceLastSelectionSource = new ConcurrentHashMap<>();
//  private ConcurrentHashMap<Long, List<Object>> instanceActiveSelectionObjects = new ConcurrentHashMap<>();
  
  private ConcurrentHashMap<UUID, List<FSId>> allNodeUsages = new ConcurrentHashMap<>();
  private ConcurrentHashMap<String, List<FSId>> allTreeUsages = new ConcurrentHashMap<>();
  private ConcurrentHashMap<String, Map<UUID, Integer>> treeNodesSelected = new ConcurrentHashMap<>();
  private ConcurrentHashMap<Long, Map<EventSource, ISelectionConverter>> fsconverter = new ConcurrentHashMap<>();
  private ConcurrentHashMap<Long, ISelectionAdapter> fsadapter = new ConcurrentHashMap<>();
  private ConcurrentHashMap<Long, ISelectionComparer> fscomparer = new ConcurrentHashMap<>();
  

  
  private Map<Long, Byte> flags = new HashMap<>();
  
  private ConcurrentHashMap<EventSource, ConcurrentHashMap<Long, IUIStateChangeListener>> sourceClassListeners =
      new ConcurrentHashMap<>();
//  private Map<FSId, IUIStateChangeListener> genericListeners =
//      new HashMap<>();
  private ReentrantLock listLock = new ReentrantLock();
  
  public void clearCaches(TreeDB db) {
    treeStateChangeListener.delete(db.getLocalName(), db.getProjectName(), db);
  }
  
  private ITreeChangeListener guiTreeList = new ITreeChangeListener() {
    
    @Override
    public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {
    }
    
    @Override
    public void deleted(TreeNode nodeDesc, byte flag, Moment m) {
      reactDeleted(nodeDesc);
    }
    
    @Override
    public void created(TreeNode node, byte flag) {
    }
    
    @Override
    public void childrenOrderChanged(TreeNode node, byte flag) {
    }
    
    @Override
    public void changed(TreeNode node, ChangesSet<Attribute> params, Moment m, byte flag) {
    }
    
    @Override
    public void changed(List<TreeNodeChangeEvent> changes) {
    }
    
    @Override
    public void beforeCreated(NodeDesc node, byte flag) {
    }
  };
  
  private ITreeStateListener treeStateChangeListener = new ITreeStateListener() {
    @Override
    public void settingsChanged(TreeDB db) {
    }
    
    @Override
    public void open(TreeDB db) {
    }
    
    @Override
    public void delete(String localName, String projectName, TreeDB db) {
      Map<UUID, Integer> allNodes = null;
      if(localName!=null) {
        allTreeUsages.remove(localName);
        allNodes = treeNodesSelected.remove(localName);
      }
      List<FSId> remFSID = new LinkedList<>();
      if(allNodes!=null) {
        for(Entry<UUID, Integer> ent:allNodes.entrySet()){
          UUID uid = ent.getKey();
          List<FSId> usa = allNodeUsages.get(uid);
          if(usa!=null)
            remFSID.addAll(usa);
        }
        for(FSId la : remFSID){
          boolean changed = false;
          if(la == null)
            continue;
          SelectionRecord nds = instanceStoredActiveSelection.get(la.getId());
          Collection<TreeNode> rem = new ConcurrentLinkedDeque<>();//removed nodes from instanceactive
          
          if(nds!=null && nds.fullSelection!=null)
            for(TreeNode nd:nds.fullSelection) {
              //for(TreeNode nd:en.getValue()) {
              UUID uid = nd.getUUId();
              if(allNodes.containsKey(uid)) {
                rem.add(nd);
                changed = true;
              }
              //}
              nds.removeAll(rem);//remove all to rem
          }
          
          //TODO check
          
          if(changed)
            updateSelection(lastPrior, la, la, false, nds, new HashSet<>());
          
        }
      }
      
      //TODO think about object selection?
      //instanceActiveSelectionObjects.clear();
      //instanceActiveSelectionObjects.clear();
      
    }
    
    @Override
    public void close(TreeDB db) {
      delete(db.getLocalName(), db.getProjectName(), db);
    }
  };
  public synchronized void setChangePending(SourcePriority state) {
    this.sourceChangePending = state;
  }
  public synchronized SourcePriority sourceChangePending() {
    return sourceChangePending;
  }
  public StackTraceElement[] getCut(StackTraceElement[] arr) {
    int lim = Math.min(5, arr.length);
    return Arrays.copyOf(arr, lim);
  }
/**
* Add listener for selection on source of some kind(for example, editor or property panel)
* @param es source to track selecton on 
* @param list selection listener
* @return true if listener is succesfully added
*/
  public boolean addSourceListener(EventSource listen, FSId where,IUIStateChangeListener list) {
    synchronized (sourceClassListeners) {
      ConcurrentHashMap<Long, IUIStateChangeListener> sl = sourceClassListeners.get(listen);
      if(sl==null)
      {
        sl = new ConcurrentHashMap<>();
        sourceClassListeners.put(listen, sl);
      }
      if(where!=null && !sl.containsKey(where.getId())) {
        sl.put(where.getId(), list);
        return true;
      }
      return false;
    }
  }
/**
* Remove listener for selection on source of some kind(for example, editor or property panel)
* @param es source to track selecton on 
* @param list selection listener
* @return true if listener is succesfully removed
*/
  public boolean removeSourceListener(EventSource es, FSId where,IUIStateChangeListener list) {
    synchronized (sourceClassListeners) {
      Map<Long, IUIStateChangeListener> sl = sourceClassListeners.get(es);
      if(sl==null)
        return false;
      try{
        if(where!=null){
          IUIStateChangeListener listOld = sl.remove(where.getId());
          if(listOld!=list && listOld!=null)
            throw new RuntimeException("Wrong change listener");
          return true;
          //return sl.remove(where.getId())!=null;
        }
        else
          return false;
      }
      finally{
        if(sl.isEmpty())
          sourceClassListeners.remove(es);
      }
    }
  }
  public boolean activateSource(SourcePriority prior, EventSource es, FSId id, boolean isSelectionSource, boolean withSelChange){
    return changeActiveSource(prior, es, id, isSelectionSource, true);
  }

  public boolean deActivateSource(SourcePriority prior, EventSource es, FSId id, boolean isSelectionSource){
    if(id == null) {
      RLogger.logDebug("change active source was called on null id "+(CoreUtils.getTrace(Thread.currentThread().getStackTrace())));
      return false;
    }
    EventSource eso = getActiveSource();
    FSId[] oldIds = null;
    boolean wasActive=eso == es;
    {
      oldIds = activeInstance.get(es);
      if(oldIds!=null && oldIds.length>0){
        if(!id.isSameAs(oldIds[0]))
          wasActive = false;
        if(deActivateId(id)){
          FSId next = getFirstActiveSource(es);
          if(next!=null && wasActive)
          {
            activateId(next, true);
            return true;
          }//else what?
        }
        return false;
      }
    }
    //not an active source but an active selection source
//    SelectionRecord oldSel = null;
//    if(oldIds!=null && oldIds.length>0 && id.isSameAs(oldIds[0])) {
//      if(deActivateId(id)) {
//        FSId next = getFirstActiveSource(es);
//        if(next!=null)
//        {
//  //        activateId(next, false);
//          return true;
//        }
//      }
//    }
//    if(oldIds!=null && oldIds.length>0)//TODO mb not first
//      oldSel = isSelectionSource?getSelection(oldIds[0]):getLastSelection(oldIds[0]);
    if(deActivateId(id))
    {
//      synchronized(this) {
//        if(lastPrior == null || prior.ordinal() >= lastPrior.ordinal() || System.currentTimeMillis()>lastTimeout) {
//          
//        }else
//          return false;
////        markChangeShelduled(id, prior);
//////        deActivateId(id);
////        callSelectionUpdate(prior, es, id, foldSel, isSelectionSource);
//      }
    }
    
    
    return false;
  }
  public void callSelectionUpdate(SourcePriority prior, EventSource es, FSId id, SelectionRecord stored, SelectionRecord visible, boolean isSelectionSource) {
    ISelectionComparer comp = fscomparer.getOrDefault(id.getId(), ISelectionComparer.DEFAULT);
    //System.out.println("Changed "+id.getObject());
    //make it non-parallel if problem persists
//    JobManager.jm.startBaseJob("Update active selection", new RunnableWithMonitor() {
//  
//      @Override
//      public void run(IProgressMonitor pm) {
//        try {
//          Thread.currentThread().join(SELECTION_CHANGED_TIMEOUT*3/4);
//        } catch (InterruptedException e) {
//        }
//        SelectionRecord stored = null, visible = null;
//        synchronized(SelectionManager.this) {
//          stored = getSelection(id);
//          visible = getLastSelection(id);
////          if(id==savedFSId && (comp.selectionChanged(id, stored, oldStored)
////                 || comp.selectionChanged(id, visible, oldVisible)))
////            return;//there are newer updates?
//        }
        updateSelectionLocal(prior, id, id, false, visible, stored, new LinkedList<>(), true, new LinkedList<>(), new HashMap<>(),true);
        synchronized(SelectionManager.this) {
          lastTimeout = -1;
          savedFSId = null;
          lastPrior = null;
        }
//      }
//    }, false);
  }

  public boolean changeActiveSource(SourcePriority prior, EventSource es, FSId id, boolean isSelectionSource, boolean changeActiveSource) {
    if(id == null) {
      RLogger.logDebug("change active source was called on null id "+(CoreUtils.getTrace(Thread.currentThread().getStackTrace())));
      return false;
    }
    EventSource eso = getActiveSource();
    FSId[] oldIds = null;
    boolean sourceChanged = false;
    if(es!=null) {
      synchronized(this) {
        if(changeActiveSource) {
          sourceChanged = activeSources == null || activeSources.isEmpty() || activeSources.iterator().next()!=es; 
          activeSources.remove(es);
          activeSources.addFirst(es);
          if(activeSources.size()>LIMIT)
            activeSources.removeLast();
        }
      }
      oldIds = activeInstance.get(es);
      if(oldIds!=null && oldIds.length>0)
        if(id.isSameAs(oldIds[0]) && !sourceChanged)
          return false;//id already active
    }
    synchronized(this) {
      if(!changeActiveSource || id!=activateId(id, true) || eso!=es) {
        SelectionRecord oldStoredSel = null, oldVisibleSel = null;
        if(oldIds!=null && oldIds.length>0){//TODO mb not first
          oldStoredSel = getSelection(id);
          oldVisibleSel = getLastSelection(id);
        }
        if(oldStoredSel == null)
          oldStoredSel = SelectionRecord.EMPTY;
        if(oldVisibleSel == null)
          oldVisibleSel = SelectionRecord.EMPTY;
        callSelectionUpdate(prior, es, id, oldStoredSel, oldStoredSel, isSelectionSource);//TODO actions with eso?
        markChangeShelduled(id, prior);
        return true;
      }
    }
    return false;
  }

  private void markChangeShelduled(FSId lastFsid,  SourcePriority prior) {
       //potential race
    savedFSId = lastFsid;
    lastTimeout = System.currentTimeMillis()+SELECTION_CHANGED_TIMEOUT;
    lastPrior = prior;
  }
  public FSId[] getActiveSources(EventSource lcnf) {
    return activeInstance.get(lcnf);
  }
  public FSId getFirstActiveSource(EventSource lcnf) {
    FSId[] ids = activeInstance.get(lcnf);
    if(ids == null || ids.length < 1)
      return null;
    return ids[0];
  }
  public EventSource getActiveSource() {
    Iterator<EventSource> it = activeSources.iterator();
    if(it.hasNext())
      return it.next();
    return null;
  }
  public void registerSelectionComparer(FSId fsid, ISelectionComparer comp){
    fscomparer.put(fsid.getId(), comp);
  }
  public void registerSelectionAdapter(FSId fsid, ISelectionAdapter adapter) {
    fsadapter.put(fsid.getId(), adapter);
  }
  public void registerSelectionConverter(FSId fsid, EventSource target, ISelectionConverter converter) {
    Map<EventSource, ISelectionConverter> map = fsconverter.get(fsid.getId());
    if(map == null) {
      map = new HashMap<>();
      fsconverter.put(fsid.getId(), map);
    }
    map.put(target, converter);
  }
  public FSId registerNewFSId(EventSource es, Object obj, boolean makeActive) {
    FSId fs = new FSId(es, obj);
    registerInstance(es,obj, fs);
    if(makeActive)
      changeActiveSource(SourcePriority.SOURCE_CHANGE, fs.getEventSource(), fs, instanceIsSource.get(fs.getId()), false);
    return fs;
  }
  
  public void registerInstance(EventSource es, Object obj, FSId fs) {
    if(obj instanceof IFSIdProvider) {
      instanceIsSource.put(fs.getId(), ((IFSIdProvider)obj).isSelectionSource());
    }else
      instanceIsSource.put(fs.getId(), false);
    synchronized (instances) {
      instances.put(fs.getId(), fs);
    }
    synchronized (allInstance) {
      LinkedList<FSId> llist = allInstance.get(es);
      if(llist == null) {
        llist = new LinkedList<>();
        allInstance.put(es, llist);
      }
      synchronized (activeInstance) {
        if(activeInstance.get(es) == null || activeInstance.get(es).length == 0)
          activeInstance.put(es, new FSId[] {fs});
      }
      llist.add(fs);
    }
    List<UUID> torem = null;
    for(Entry<UUID, List<FSId>> en: allNodeUsages.entrySet()) {
      if(en.getValue().contains(fs)) {
        en.getValue().remove(fs);
        if(en.getValue().isEmpty()) {
          if(torem == null) 
            torem = new LinkedList<>();
          torem.add(en.getKey());
        }
      }
    }
    if(torem!=null)
      for(UUID uid:torem)
        allNodeUsages.remove(uid);
    List<String> toremS = null;
    for(Entry<String, List<FSId>> en : allTreeUsages.entrySet()) {
      if(en.getValue().contains(fs)) {
        en.getValue().remove(fs);
        if(en.getValue().isEmpty()) {
          if(toremS == null) 
            toremS = new LinkedList<>();
          toremS.add(en.getKey());
        }
      }
    }
    if(toremS!=null)
      for(String s: toremS)
        allTreeUsages.remove(s);
  }
  
  public void unregisterInstance(FSId id) {
    EventSource source = id.getEventSource();
    synchronized (instances) {
      if(instances.remove(id.getId())==null)
        RLogger.logDebug("attempt to unregister not removed node");
    }
    Boolean isSource = instanceIsSource.remove(id.getId());
    fsconverter.remove(id.getId());
    fsadapter.remove(id.getId());
    fscomparer.remove(id.getId());

    instanceStoredActiveSelection.remove(id.getId());
    instanceVisibleActiveSelection.remove(id.getId());
    instanceLastSelectionSource.remove(id.getId());
    flags.remove(id.getId());
    LinkedList<FSId> list = allInstance.get(source);
    if(list!=null) {
      Iterator<FSId> lit = list.iterator();
      while(lit.hasNext()) {
        FSId next = lit.next();
        if(next.getRootId().equals(id) || next.equals(id)) {
          lit.remove();
          allInstance.put(source, list);
        }
      }
    }
    boolean isMain = source == getActiveSource();
    boolean wasActive = isMain && isActive(id);
    {
      deActivateSource(SourcePriority.SOURCE_CHANGE, id.getEventSource(), id, isSource == null ? false: isSource);
      if(wasActive)
        fireSourceChange(id, id, null);
    }
    //Update cache from newSelection
  }
  
  private void reactDeleted(TreeNode node) {
    List<FSId> ids = allNodeUsages.get(node.getUUId());
    TreeDB rem;
    if(ids != null) {
      List<FSId> tobeup = new LinkedList<>(), rem2 = null;
      for(FSId id:ids) {
        //Remove node from fsid 
        SelectionRecord nodes = (SelectionRecord) instanceStoredActiveSelection.get(id.getId());
        String type = node.getType();
        //TreeNode[]old = nodes.get(type);
        if(recRemoved(nodes, node))
          tobeup.add(id);//mark that source needs to fire update

        if(rem2 == null)
          rem2 = new LinkedList<>();
        rem2.add(id);
      }
      if(rem2!=null)
        ids.removeAll(rem2);
      //remove node from selected on specific tree
      String dbId = node.getTreeDB().getSemiLocalName();
      Map<UUID, Integer> tnodes = treeNodesSelected.get(dbId);
      UUID uid = node.getUUId();
      Integer cnt = tnodes.get(uid);
      if(cnt != null)
        if(cnt == 1)
          tnodes.remove(uid);
        else
          tnodes.put(uid, cnt-1);
      if(tnodes.size() == 0)
      {//if there are no more nodes on tree selected then remove listener on it
        tnodes.remove(node.getUUId());
        allTreeUsages.remove(dbId);
        if(!trackerInited){
            node.getTreeDB().getTracker().addListener(Moment.BEFORE, treeStateChangeListener);
            trackerInited = true;//TODO in this version is not required
            //can be rewrited to drop on all dbs to be closed\removed state
        }
        node.getTreeDB().removeGUIListener(guiTreeList);
      }
      for(FSId tu: tobeup) { 
        if(isActive(tu))//TODO think on source here
          fireSourceChange(tu, tu, null);
      }
    }
  }
  private boolean recRemoved(SelectionRecord nodes, TreeNode node) {
    boolean ret = false;
    if(nodes.remove(node))
      ret = true;
    for(TreeNode chi:node.getWorkChildren()) 
      if(recRemoved(nodes, chi))
        ret = true;
    return ret;
  }
  private TreeNode[] removeNodeFromArray(TreeNode[] treeNodes, TreeNode node) {
    TreeNode[] arr = new TreeNode[treeNodes.length-1];
    int i = 0;
    for(TreeNode newa:treeNodes)
      if(newa != node)
        arr[i++] = newa;
    return arr;
  }
  
  private static SelectionRecord toSelected(TreeNode ... sel){
    return new SelectionRecord(sel);
  }
  public long updateSelection(SourcePriority prior, FSId fsid, TreeNode ... sel) {
    return updateSelection(prior, fsid, fsid, true, toSelected(sel), new HashSet<EventSource>());
  }
  public long updateSelection(SourcePriority prior, FSId fsid, SelectionRecord sr) {
    return updateSelection(prior, fsid, fsid, true, sr, new HashSet<EventSource>());
  }
  public long updateSelection(SourcePriority prior, FSId rootfsid,FSId fsid, boolean sendGlobal, SelectionRecord sel, Collection<EventSource> fired) {
    if(fsid == null)
      return -1;
    if(rootfsid == savedFSId || lastPrior == null || prior.ordinal() >= lastPrior.ordinal() || System.currentTimeMillis()>lastTimeout) {
    }else
      return -1;
    markChangeShelduled(rootfsid, prior);
    return updateSelectionLocal(prior, fsid, fsid, sendGlobal, sel, sel, fired, true, new LinkedList<>(), new HashMap<>(), false);
  }
  public long updateSelectionLocal(SourcePriority prior, FSId rootfsid,FSId fsid, boolean sendGlobal, SelectionRecord visualSel, SelectionRecord storedSel, Collection<EventSource> fired, boolean fire, List<FSId>toUpdate,  Map<FSId, SelectionRecord>updateSelections, boolean forced) {
    if(fsid == null)
      return -1;
    long opId = -1;
    Map<FSId, SelectionRecord>newSelections = null;
    Map<FSId, SelectionRecord>newStoredSelections = null;
    synchronized (this) {
      try {
        if(visualSel == null)
          visualSel = SelectionRecord.EMPTY;
        SelectionRecord oldStoredSel = getSelection(fsid);
        SelectionRecord oldSel = getLastSelection(fsid);
//        if(oldSel == null)
//          oldSel = oldStoredSel;
        FSId oldSource = instanceLastSelectionSource.get(fsid.getId());
        boolean activeSource = getActiveSource()==fsid.getEventSource() && isActive(fsid);
        ISelectionAdapter adapter = fsadapter.getOrDefault(fsid.getId(), ProjectSelectionAdapter.getOne());
        boolean forceUpdate = forced;
        ISelectionComparer comp = fscomparer.getOrDefault(fsid.getId(), 
          ISelectionComparer.DEFAULT);
        if(adapter!=null)
        {
          Pair<SelectionRecord, SelectionRecord> ret = adapter.adaptChange(rootfsid, visualSel, oldSel, storedSel, oldStoredSel);
          if(ret.getFirst()!=null)
            visualSel = ret.getFirst();
          if(ret.getSecond()!=null)
            storedSel = ret.getSecond();
        }
        if(!forceUpdate && !comp.selectionChanged(rootfsid, oldSel, visualSel) && 
          (storedSel == null || !comp.selectionChanged(rootfsid, storedSel, oldStoredSel)))
            return -1;
        opId = MAX_OP_ID++;
        //TODO back returing selection wave 
      
        Set<TreeDB> dbs = new HashSet<>(), added = new HashSet<>();
        //remove old nodes from selection
  
        boolean factChanged = false;
        //set new selection
        if(!(fsid instanceof EventFSId)) 
          factChanged = true;
        if(storedSel!=null) {
          for(TreeNode node : oldStoredSel.fullSelection) {
            if(node == null) {
              RLogger.logError("Null node on selection");
              continue;
            }
            if(node.getTreeDB().isDisposed()) {
              RLogger.logDebug("Disposed tree was in old selection");
              continue;
            }
            UUID uid = node.getUUId();
            dbs.add(node.getTreeDB());
            List<FSId> list = allNodeUsages.get(uid);
            if(list!=null)
              list.remove(fsid);
            String oldName = node.getTreeDB().getSemiLocalName();
            if(oldName!=null) {
              Map<UUID, Integer> nodes = treeNodesSelected.get(oldName);
              if(nodes!=null) {
                Integer cnt = nodes.get(uid);
                if(cnt!=null)
                  if(cnt == 1)
                    nodes.remove(uid);
                  else
                    nodes.put(uid, cnt-1);
              }
            }
          }
        }
        instanceVisibleActiveSelection.put(fsid.getId(), visualSel);
        if(storedSel!=null) {
          instanceStoredActiveSelection.put(fsid.getId(), storedSel);
        }
        
      
        //update cache for selection
        if(storedSel!=null) {
          for(TreeNode node : storedSel.fullSelection) {
            {
              if(node == null) {
                RLogger.logError("null node on selection");
                continue;
              }
              UUID id = node.getUUId();
              List<FSId> ids = allNodeUsages.get(id);
              if(ids == null) {
                ids = new LinkedList<FSId>();
                allNodeUsages.put(id, ids);
              }
              //update map uuid->fsids
              ids.add(fsid);
              //some trees are still in selection
              dbs.remove(node.getTreeDB());
              //maybe some new trees
              added.add(node.getTreeDB());
              //marks nodes for treedb as selected
              Map<UUID, Integer> nids = treeNodesSelected.get(node.getTreeDB().getSemiLocalName());
              if(nids == null) {
                nids = new HashMap<>();
                treeNodesSelected.put(node.getTreeDB().getSemiLocalName(), nids);
              }
              Integer cnt = nids.get(id);
              if(cnt == null)
                cnt = 0;
              nids.put(id, cnt+1);
            }
          }
        //remove trees that are not presented in sel
          for(TreeDB db:dbs) {
            String did = db.getSemiLocalName();
            if(did == null)
              continue;
            Map<UUID, Integer> nodes = treeNodesSelected.get(did);
            if(nodes == null || nodes.isEmpty()) {
              allTreeUsages.remove(did);
              treeNodesSelected.remove(did);
              db.removeGUIListener(guiTreeList);
            }
          }
          //new treedb add
          for(TreeDB db:added) {
            String id = db.getSemiLocalName();
            List<FSId> ids = allTreeUsages.get(id);
            if(ids == null) {
              ids = new LinkedList<>();
              allTreeUsages.put(id, ids);
            }
            if(!ids.contains(fsid)) {
              ids.add(fsid);
              if(!trackerInited){
                db.getTracker().addListener(Moment.BEFORE, treeStateChangeListener);
                trackerInited = true;
              }
              db.addGUIListener(guiTreeList);
            }
          }
        }
        EventSource es = fsid.getEventSource();
        fired.add(es);
        toUpdate.add(fsid);
        updateSelections.put(fsid, visualSel);
      //  updateSelections.put(fsid, visualsel);
     //  SelectionManager. fireSourceChange(rootfsid, fsid);
        //change related selection only for active panels
        EventSource[]related = activeSource ? getRelated(es): NO_SOURCES;
        FSId basefsid = fsid;
        List<FSId> toUpdateSelection = null;
     
        for(EventSource esa:related){
          if(!fired.contains(esa)){
            FSId[] fsidarr = getActiveSources(esa);
            if(fsidarr!=null && fsidarr.length>0){
              ISelectionConverter conv = findConverter(fsid, esa);
              SelectionRecord newSelection = null;
              for(FSId fsida: fsidarr) {
                newSelection = visualSel;
                SelectionRecord oldRec = getLastSelection(fsida);
                if(oldRec == null)
                  oldRec = getSelection(fsida);
                Pair<Boolean, SelectionRecord> convResult = null;
                ISelectionComparer compa = fscomparer.getOrDefault(fsida.getId(), ISelectionComparer.DEFAULT); 
                if(conv!=null)
                  convResult = conv.convertSelection(fsida, visualSel, oldRec);
                FSId comboFSId = new EventFSId(basefsid, fsida);
                boolean upped = false;
                if(convResult!=null) {
                  newSelection = convResult.getSecond();
                  if(fsida.getEventSource()!=basefsid.getRootId().getEventSource() && convResult.getFirst()){
                    if(newStoredSelections==null)
                      newStoredSelections = new HashMap<>();
                    newStoredSelections.put(comboFSId, newSelection);
                    upped = true;
                  }
                }
                fired.add(esa);
                //toUpdate.add(basefsid);
                if(toUpdateSelection == null || newSelections == null) {
                  toUpdateSelection = new LinkedList<>();
                  newSelections = new HashMap<>();
                }
                if(upped || compa.selectionChanged(rootfsid, newSelection, oldRec)) {
                  toUpdateSelection.add(comboFSId);
                  newSelections.put(comboFSId, newSelection);
                }
              }
            }
          }
        }
        if(toUpdateSelection!=null)
          for(FSId comboFSId:toUpdateSelection)
            updateSelectionLocal(prior, rootfsid, comboFSId, sendGlobal, newSelections==null?null:newSelections.get(comboFSId), newStoredSelections==null?null:newStoredSelections.get(comboFSId), fired, false, toUpdate, updateSelections, false);
      } finally {
        if(fire)
          sourceChangePending = SourcePriority.SOURCE_CHANGE.NO;
      }
    }
    if(fire)
      for(Entry<FSId, SelectionRecord> fs:updateSelections.entrySet())
        fireSourceChange(rootfsid, fs.getKey(), fs.getValue());
    instanceLastSelectionSource.put(fsid.getId(), rootfsid);
    if(fire ){
      for(Entry<FSId, SelectionRecord> en: updateSelections.entrySet()){
        instanceVisibleActiveSelection.put(en.getKey().getId(), en.getValue());
        instanceLastSelectionSource.put(en.getKey().getId(), rootfsid);
      }
    }
    return opId;
  }

  private ISelectionConverter findConverter(FSId fsid, EventSource esa) {
    if(fsid == null)
      return null;
    Map<EventSource, ISelectionConverter> convs =  fsconverter.get(fsid.getId());
    if(convs!=null){
      ISelectionConverter conv = convs.get(esa);
      if(conv!=null)
        return conv;
    }
    FSId base = fsid.getBaseId();
    if(base != fsid)
      return findConverter(base, esa);
    return null;
  }

  private static EventSource[]
      RELATED_EMPTY = new EventSource[0],
      RELATED_EXPLORER = new EventSource[]{EventSource.PROPERTIES,EventSource.EDITOR, EventSource.LCNF},
      RELATED_OUTLINE = new EventSource[] {EventSource.EDITOR},
      RELATED_PROPERTIES = new EventSource[] {EventSource.EDITOR, EventSource.EXPLORER},
      RELATED_DIALOG = new EventSource[] {EventSource.EXPLORER},
      RELATED_LCNF = new EventSource[] {EventSource.EXPLORER, EventSource.PROPERTIES, EventSource.EDITOR},
      RELATED_EDITOR = new EventSource[] {EventSource.OUTLINE, EventSource.PROPERTIES, EventSource.EXPLORER, EventSource.LCNF};
  
  private EventSource[] getRelated(EventSource eventSource) {
    switch(eventSource) {
      case EXPLORER: return RELATED_EXPLORER;
      case EDITOR: return RELATED_EDITOR;
      case OUTLINE: return RELATED_OUTLINE;
      case LCNF: return RELATED_LCNF;
      case DIALOG: return RELATED_DIALOG;
      case PROPERTIES: return RELATED_PROPERTIES;
    }
    return RELATED_EMPTY;
  }
//  public void updateSelectionObjects(FSId fsid, List<Object> sel) {
//    //TODO treedb or resdelete?
//    instanceActiveSelectionObjects.put(fsid.getId(), sel);
//    if(isActive(fsid))
//      fireEventsObjects(fsid);
//  }

  public SelectionRecord getSelection(){
    Iterator<EventSource> selSource = activeSources.iterator();
    if(!selSource.hasNext())
      return SelectionRecord.EMPTY;
    while(selSource.hasNext()) {
      EventSource es = selSource.next();
      FSId ai = getFirstActiveSource(es);
      if(ai == null)
        continue;
      SelectionRecord sel = instanceStoredActiveSelection.get(ai.getId());
      if(sel != null) 
        return sel;
    }
    //TODO mb count instance-> instance change in  
    return SelectionRecord.EMPTY;
  }
  public SelectionRecord getLastSelection(FSId id){
    if(id == null)
      return SelectionRecord.EMPTY;
    return instanceVisibleActiveSelection.get(id.getId());
  }

  public SelectionRecord getSelection(FSId id){
    if(id == null)
      return SelectionRecord.EMPTY;
    return instanceStoredActiveSelection.getOrDefault(id.getId(), SelectionRecord.EMPTY);
  }
//
//  public List<Object> getSelectionObjects(FSId id){
//    if(id == null)
//      return Collections.emptyList();
//    return instanceActiveSelectionObjects.getOrDefault(id.getId(),
//      Collections.emptyList());
//  }
  public SelectionRecord getSelection(EventSource es){
    FSId l = getFirstActiveSource(es);
    if(l == null)
      return SelectionRecord.EMPTY;
    return instanceStoredActiveSelection.getOrDefault(l.getId(), SelectionRecord.EMPTY);
  }

  public TreeNode[] getSelection(String typeName, TreeNode[] treeNodes) {
    SelectionRecord sr = getSelection();
    Collection<TreeNode> ret = sr.getByType(typeName);
    if(ret == null || ret.size() == 0)
      return treeNodes;
    return ret.toArray(new TreeNode[ret.size()]);
  }
  public Collection<TreeNode> getSelection(EventSource es, String type){
    FSId l = getFirstActiveSource(es);
    if(l == null)
      return Collections.emptyList();
    return instanceStoredActiveSelection.getOrDefault(l.getId(), SelectionRecord.EMPTY).getOrDefault(type, Collections.emptyList());
  }
  
  private Collection<TreeNode> getFiltered(Collection<TreeNode> sel, String type) {
    Collection<TreeNode> ret = null;
    for(TreeNode node:sel) {
      if(node == null)
        continue;
      if(node.getType().equals(type)) {
        if(ret == null)
          ret = new ConcurrentLinkedDeque<>();
        ret.add(node);
      }
    }
    return ret == null ? Collections.emptyList() : ret;
  }
//  public List<Object> getSelectionObjects(){
//    Iterator<EventSource> selSource = activeSources.iterator();
//    if(!selSource.hasNext())
//      return Collections.emptyList();
//    while(selSource.hasNext()) {
//      EventSource es = selSource.next();
//      FSId ai = getFirstActiveSource(es);
//      if(ai == null)
//        continue;
//      List<Object> sel = instanceActiveSelectionObjects.get(ai.getId());
//      if(sel != null) 
//        return sel;
//    }
//    //TODO mb count instance-> instance change in  
//    return Collections.emptyList();
//  }
  public static SelectionManager getOne() {
    return sm;
  }

  public static boolean selectionIsChanged(SelectionRecord an, SelectionRecord nodes) {
    if(nodes==null)
      nodes = SelectionRecord.EMPTY;
    if(an==null)
      an = SelectionRecord.EMPTY;
    return !an.isSameWith(nodes);
//    if(an.fullSelection.size()!=nodes.fullSelection.size())
//      return true;
//    Iterator<TreeNode> i1 = an.fullSelection.iterator(),
//      i2 = nodes.fullSelection.iterator();
//    while(i1.hasNext() && i2.hasNext())
//    {
//      TreeNode o = i1.next(), o2 = i2.next();
//      if(o != o2 && !o.equals(o2))//TODO test
//        return true;
//    }
//    if(i1.hasNext() || i2.hasNext())
//      return true;
//    return false;
  }
//  private void fireEventsObjects(FSId sourceClass) {
//    //EventSource activeSource = getActiveSource();
//    EventSource argumentSource = sourceClass.getEventSource();
//    if(!isActive(sourceClass))
//      return;
//    List<Object> sourceSel = instanceActiveSelectionObjects.get(sourceClass.getId());
//    listLock.lock();
//    Map<Long, IUIStateChangeListener> list = null;
//    synchronized (sourceClassListeners) {
//    try{
//        list = sourceClassListeners.get(argumentSource);
//      }finally{
//        listLock.unlock();
//      }
//    }
//    if(list!=null){
//        listLock.lock();
//        try{
//            list = new HashMap<>(list);
//        }finally{
//            listLock.unlock();
//        }
//      list.entrySet().stream().forEach(one->{
//        one.getValue().newActiveObjects(sourceClass, sourceSel);
//      });
//    }
//  }
  private boolean deActivateId(FSId id){
    synchronized (activeInstance) {
      FSId[] sourceId = activeInstance.get(id.getEventSource());  
      FSId oldInst = null;
      for(FSId fs: sourceId) {
        Object obj = fs.getObject();
        if(id.isSameAs(fs)){
          oldInst = fs;
          break;
        }
      }
      try {
        if(oldInst!=null) {
          boolean ret = true;
          if(sourceId.length == 0) {
            activeInstance.remove(id.getEventSource());
            ret = true;
          }
          if(!ret) {
            FSId[]newSources = new FSId[sourceId.length-1];
            int i = 0;
            for(FSId fs: sourceId)
              if(fs != oldInst)
                newSources[i++] = fs;
            activeInstance.put(id.getEventSource(), newSources);
          }
          return true;
        }
        return false;
      }finally {
  //      updateSelection(SourcePriority.SOURCE_CHANGE, id, SelectionRecord.EMPTY);
      }
    }
  }
  private void swap(FSId[]arr, int ind1, int ind2){
    FSId b = arr[ind1];
    arr[ind1] = arr[ind2];
    arr[ind2] = b;
  }
  private FSId activateId(FSId id, boolean withActivation){
    if(id == null)
      return null;
    synchronized (activeInstance) {
      FSId[] sourceId = activeInstance.get(id.getEventSource());  
      FSId oldInst = null;
      int inc = 0;
      FSId oldFirst = null;
      FSId[]newSourceId = null;
      if(sourceId != null && sourceId.length>0){
        oldFirst = sourceId[0];
        for(FSId fs: sourceId){
          if(id.isSameAs(fs)){
            oldInst = fs;
            break;
          }
          inc++;
        }
        if(oldInst!=null && withActivation){
          if(inc>0){
            for(int i = inc;i>0;i--)
              swap(sourceId, i, i-1);
          }
          return oldFirst;
        }
        newSourceId = new FSId[sourceId.length+1];
      }else{
        newSourceId = new FSId[1];
      }
      if(withActivation){
        newSourceId[0] = id;
        if(sourceId!=null)
          for(int i = 0;i<sourceId.length;i++)
            newSourceId[i+1] = sourceId[i];
      }else{
        if(sourceId!=null)
          for(int i = 0;i<sourceId.length;i++)
            newSourceId[i] = sourceId[i];
        newSourceId[newSourceId.length-1] = id;
      }
      activeInstance.put(id.getEventSource(), newSourceId);
      return oldFirst;
    }
  }
  public boolean isActive(FSId sourceClass) {
    if(sourceClass == null)
      return false;
    FSId[] sourceId = activeInstance.get(sourceClass.getEventSource());
    for(FSId fs: sourceId)
      if(sourceClass.isSameAs(fs) || fs.isSameAs(sourceClass)) 
        return true;
    return false;
  }
  private void fireSourceChange(FSId rootClass, FSId sourceId, SelectionRecord rec) {
    //EventSource activeSource = getActiveSource();
    //EventSource argumentSource = sourceClass.getEventSource();
    //FSId activeId = activeInstance.get(activeSource);
    SelectionRecord sourceSel = rec == null ? sourceId==null?null 
            :instanceVisibleActiveSelection.get(sourceId.getId()): rec;//тут вопрос, что лучше
    if(sourceSel == null)
      sourceSel = sourceId == null ? null : instanceStoredActiveSelection.get(sourceId.getId());
    if(sourceId!=null) {
      EventSource targetSource = sourceId.getEventSource();
      Map<Long, IUIStateChangeListener> list = null;
      synchronized(sourceClassListeners){
        list = sourceClassListeners.get(targetSource);
      }
      final SelectionRecord fSourceSel = sourceSel;
      //FSId active = getActiveSource(targetSource);
      if(fSourceSel!=null) {
        if(list!=null && isActive(sourceId)){
          list = new HashMap<>(list);
          list.entrySet().stream().forEach(one->{
            one.getValue().newActiveNodes(sourceId, fSourceSel);
          });
        }
      }
    }
  } 
  public static Collection<TreeNode> getAllNodes(SelectionRecord sel) {
    return sel.fullSelection;
  }
  public static TreeNode getFirstNode(SelectionRecord mps) {
    return mps.fullSelection.iterator().next();
  }
  public void setFlag(FSId fsida, Byte flag) {
    flags.put(fsida.getId(), flag);
  }
  public boolean isMarkup(FSId fsida) {
    return (getFlags(fsida) & MARKUP_FLAG) == MARKUP_FLAG;
  }
  private byte getFlags(FSId fsida) {
    return flags.getOrDefault(fsida.getId(),(byte)0);
  }
  public Collection<TreeNode> getConverted(FSId baseEvent, FSId fsid, Collection<TreeNode> nodes) {
    ISelectionConverter conv = findConverter(baseEvent, fsid.getEventSource());
    SelectionRecord sr = new SelectionRecord(nodes);
    if(conv!=null) {
      Pair<Boolean,SelectionRecord> convResult = conv.convertSelection(fsid, sr, getSelection(fsid));
      if(convResult!=null) {
        return convResult.getSecond().fullSelection;
      }
      return sr.fullSelection;
    }
    return null;
  }
  public FSId detectSource(Object source) {
    if(source instanceof IFSIdProvider)
      return((IFSIdProvider)source).getFSId();
    return null;//TODO better detection?
  }
  public Collection<Object> getSelectionObjects() {
    SelectionRecord sr = getSelection();
    if(sr == null)
      return Collections.emptyList();
    return sr.fullSelectionObjects;
  }

}
