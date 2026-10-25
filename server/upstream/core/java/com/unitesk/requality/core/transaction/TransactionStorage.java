package com.unitesk.requality.core.transaction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;

import org.apache.commons.io.IOUtils;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.IMonitorManager;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.Version;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.instancer.TreeInstancer;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.AbstractTreeStorage;
import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.tool.CoreUtils;

public class TransactionStorage extends AbstractTreeStorage {
  private class TreeOperationState{
    public TreeOperation op;
    public boolean performed;
    public TreeOperationState(TreeOperation op, boolean st) {
      this.op = op;
      this.performed = st;
    }
  }
  private LinkedList<TreeOperationState> rootOperation = new LinkedList<>();
  public class OperationData {
    private TreeOperation operation;
    private Object data;

    public OperationData(TreeOperation operation, Object data) {
      this.operation = operation;
      this.data = data;
    }

    public TreeOperation getOperation() {
      return operation;
    }

    public Object getData() {
      return data;
    }
  }

  @Override
  public Map<String, Version> getVersions(UUID uuid) {
    Map<String, Version> base = new HashMap<>(storage.getVersions(uuid));
    if (!versstorage.containsKey(uuid)) return base;
    return super.getVersions(uuid);
  }

  @Override
  public void saveVersions(UUID uuid, ChangesSet<Version> changes, byte flag) {
    IStorageDeltaListener delta = getDeltaTarget();
    Map<String, Version> // oldAttributes = new
        // HashMap<String,Version>(getVersions(uuid)),
        newA = new HashMap<>(changes.getNewMap());
    for (String s : newA.keySet()) newA.put(s, newA.get(s).clone());
    Map<String, Version> old = new HashMap<>(getVersions(uuid));
    for (String s : old.keySet()) old.put(s, old.get(s).clone());

    boolean derived = !rootOperation.isEmpty();
    SaveVersions saveOperation = new SaveVersions(derived, uuid, changes, old, newA, (byte)(flag));
    TreeOperationState parent = null;
    if(derived) {
      parent = rootOperation.iterator().next();
      parent.op.addDerived(saveOperation, parent.performed);
    }
    TreeOperationState tos = new TreeOperationState(saveOperation,false); 
    rootOperation.addFirst(tos);
    if(delta!=null)
      delta.versionChanged(uuid, changes, Moment.BEFORE, flag);
    super.saveVersions(uuid, changes, flag);
    tos.performed = true;
    addOperation(saveOperation);
    
    // is it needed?
    if(delta!=null)
      delta.versionChanged(uuid, changes, Moment.AFTER, flag);
    
    rootOperation.remove(tos);
    // super.saveVersions(uuid, attributes);
  }

  private static final String TMP_RESOURCE_PREFIX = "requality";
  private ITreeStorage storage;
  private TreeInstancer vstorage;
  private Map<UUID, String> newId = new HashMap<>();
  private Map<UUID, String> oldId = new HashMap<>();
  private Map<UUID, Collection<UUID>> newChildren = new ConcurrentHashMap<>();
  private Map<UUID, Collection<UUID>> oldChildren = new ConcurrentHashMap<>();
  private Map<UUID, Map<String, AttributeDefinition>> newAttr =
      new ConcurrentHashMap<>();
  private Map<UUID, Map<String, AttributeDefinition>> oldAttr =
      new ConcurrentHashMap<>();
  private Map<UUID, HashMap<String, String>> resourсes =
      new LinkedHashMap<>();

  private ConcurrentLinkedQueue<TreeOperation> operationQueue =
      new ConcurrentLinkedQueue<>();

  private List<ITransactionListener> tListeners;

  private String transactionName = "complex operation";
  private boolean execute = true;
  private boolean verbose = false;

  public TransactionStorage(
      ITreeStorage storage, TreeInstancer vstorage, List<ITransactionListener> tListeners) {
    this.storage = storage;
    this.vstorage = vstorage;
    this.tListeners = tListeners;
    cs = storage.getCacheStorage();
  }

  public CacheStorage getCacheStorage(String id){
    return storage.getCacheStorage(id);
  }
  @Override
  public CacheStorage getCacheStorage(TreeDB db){
    return getCacheStorage();
  }
  public String getVisibleName() {
    return transactionName;
  }

  public String getProjectName() {
    return storage.getProjectName();
  }
  public String getLocalName() {
    return storage.getLocalName();
  }


  public void setName(String name) {
    transactionName = name;
  }

  @Override
  public UUID getRootNode() {
    return storage.getRootNode();
  }

  @Override
  public UUID getParent(UUID uuid) {
    for (UUID pUUId : newChildren.keySet()) {
      if (pUUId != null
          && newChildren.get(pUUId) != null
          && newChildren.get(pUUId).contains(uuid)) {
        return pUUId;
      }
    }
    UUID oldParentUUID = getOldParent(uuid);
    if (oldParentUUID != null) { // if node was deleted
      return oldParentUUID;
    }
    UUID pid = storage.getParent(uuid);
    if (pid != null) return pid;
    // if(vstorage == null)
    // return null;
    return null;
    // return vstorage.getStoredParent(uuid);
  }

  @Override
  public String getNodeId(UUID uuid) {
    String id = null;
    if (newId.containsKey(uuid)) {
      id = newId.get(uuid);
    }
    if (id == null) id = storage.getNodeId(uuid);
    // if(id == null && vstorage!=null)
    // id = vstorage.getNodeId(uuid);
    return id;
  }

  @Override
  public Map<String, AttributeDefinition> getStorageAttributes(UUID uuid) {
    return currentAttrs(uuid);
  }

  @Override
  public Collection<UUID> getChildren(UUID uuid) {
    return currentChildren(uuid);
  }

  @Override
  public boolean canLoad(UUID uuid) {
    if (uuid.equals(getRootNode())) return true;
    if (getParent(uuid) == null) {
      return false;
    }
    return currentChildren(getParent(uuid)).contains(uuid);
  }

  @Override
  public URI getNodeResourcePath(UUID uuId){
    URI path = null;
    URI s = storage.getNodeResourcePath(uuId);
    if (s != null) path = s;
    if (path == null) {
      UUID par = getParent(uuId);
      if (par == null) return null;
      s = getNodeResourcePath(par);
      URI ppath = null;
      if (s != null) ppath = new File(s).getParentFile().toURI();
      if (ppath != null) {
        try {
          return new URI(
              ppath
                  + CoreUtils.URLEncode(
                      "/"
                          + getNodeId(getParent(uuId))
                          + "/"
                          + getNodeId(uuId)
                          + ResManager.RESOURCES_SUFFIX));
        } catch (URISyntaxException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
    return path;
  }

  @Override
  public Set<String> listResources(UUID uuid) {
    return new HashSet<>(currentResources(uuid).keySet());
  }

  public ITreeStorage getStorage() {
    return storage;
  }

  /**
   * VSCODE-PATCH: the nodes this transaction may have changed: created, deleted, renamed, moved (with their old and new
   * parents), or with attributes or resources it read or wrote. The MCP server of the VS Code port compares their
   * states before and after the transaction to record a change it can revert.
   */
  public java.util.Set<UUID> getTouchedNodes() {
    java.util.Set<UUID> s = new java.util.HashSet<>();
    s.addAll(newId.keySet());
    s.addAll(oldId.keySet());
    s.addAll(newAttr.keySet());
    s.addAll(oldAttr.keySet());
    s.addAll(resourсes.keySet());
    for (Map.Entry<UUID, Collection<UUID>> e : newChildren.entrySet()) {
      Collection<UUID> old = oldChildren.get(e.getKey());
      if (old != null && old.equals(e.getValue()))
        continue;
      s.add(e.getKey());
      // the children that came or went (created, deleted, moved), not the siblings that stayed
      for (UUID c : e.getValue())
        if (old == null || !old.contains(c))
          s.add(c);
      if (old != null)
        for (UUID c : old)
          if (!e.getValue().contains(c))
            s.add(c);
    }
    s.remove(null);
    return s;
  }

  private UUID getOldParent(UUID uuid) {
    for (UUID pUUId : oldChildren.keySet()) {
      if (oldChildren.get(pUUId).contains(uuid)) {
        return pUUId;
      }
    }
    return null;
  }

  @Override
  public void createStorageNode(NodeDescSet rdesc,String templ, byte flag) {
    IStorageDeltaListener delt = getDeltaTarget();
    
    boolean derived = !rootOperation.isEmpty();
    CreateNode createOp =
        new CreateNode(derived, rdesc, templ, (byte)(flag));
    TreeOperationState parent = null;
    if(derived) {
      parent = rootOperation.iterator().next();
      parent.op.addDerived(createOp, parent.performed);
    }
    TreeOperationState tos = new TreeOperationState(createOp,false); 
    rootOperation.addFirst(tos);
    if (delt != null) delt.beforeCreated(rdesc, templ, (byte)(flag));//here to be problems
    
    LinkedList<NodeDesc> list = rdesc.allNodesCreate;
    ListIterator<NodeDesc> in = list.listIterator();
    while(in.hasNext()) {
      NodeDesc desc = in.next();
      newId.put(desc.getUUId(), desc.getId());
      oldId.put(desc.getUUId(), null);
      currentChildren(desc.getWorkParentUUID()).add(desc.getUUId());

      newAttr.put(desc.getUUId(), new HashMap<>(desc.getAttributes()));
      oldAttr.remove(desc.getUUId());

    }
    tos.performed = true;

    addOperation(createOp);
      
    // is it needed?
    if (delt != null) delt.created(rdesc, templ, (byte)(flag));

    rootOperation.remove(tos);
  }

  @Override
  public void remove(NodeDescSet sdesc, byte flag) {
    IStorageDeltaListener delt = getDeltaTarget();
    CacheStorage csa = cs;
   
    boolean derived = !rootOperation.isEmpty();
    DeleteNode deleteOperation = new DeleteNode(derived, sdesc, (byte)(flag));
    TreeOperationState parent = null;
    if(derived) {
      parent = rootOperation.iterator().next();
      parent.op.addDerived(deleteOperation, parent.performed);
    }
    TreeOperationState tos = new TreeOperationState(deleteOperation,false); 
    rootOperation.addFirst(tos);
    CacheTransactionDescription td = csa.startCacheTransaction("remove Nodes "+sdesc, 2);
    try {
      if (delt != null) delt.beforeDeleted(sdesc, flag);
      LinkedList<NodeDesc> fullSet = sdesc.allNodesRemove;
      for(NodeDesc desc: fullSet) {
        UUID uid = desc.getUUId();
        UUID pUUID = desc.getWorkParentUUID();
        Map<String, AttributeDefinition> attrs = desc.getAttributes();
        String id = desc.getId();
    
        oldAttr.put(uid, attrs);
        oldAttr.remove(uid);
        
        newId.remove(uid);
        oldId.put(uid, id);
        
        if (pUUID != null) currentChildren(pUUID).remove(uid);
    
        // TODO: transaction remove resources
        Set<String> resources = desc.resourcesSet();
        for (String key : resources) {
          deleteResource(uid, key, flag);
        }
      }
      tos.performed = true;
      
      addOperation(deleteOperation);
      
      if (delt != null) delt.deleted(sdesc, (byte)(flag));

      rootOperation.remove(tos);
    }finally {
      if(td!=null && td.error==null)
        csa.commit(td);
    }
  }

  private void addOperation(TreeOperation op) {
    if(!op.isDerived)
      operationQueue.add(op); 
  }


  @Override
  public void changeNodeId(UUID uuid, String newNodeId, byte flag) {
    IStorageDeltaListener delt = getDeltaTarget();
    String oldNodeId = getNodeId(uuid);
//    if (oldId != null && !oldId.equals(newId)) {
//      //TODO add id change instead?
////      getDeltaTarget()
////          .changed(
////              uuid,
////              AttributeStorage.formChangesSet(uuid, TreeNode.ATTR_ID, oldNodeId),
////              AttributeStorage.formAttributeDefMap(TreeNode.formIdAttribute(uuid, newNodeId)),
////              Moment.BEFORE,
////              true);
//      getDeltaTarget().moved(uuid, getParent(uuid), getParent(uuid), Moment.BEFORE, true);
//    }

    boolean derived = !rootOperation.isEmpty();
    
    RenameNode moveOperation = new RenameNode(derived, uuid, oldNodeId, newNodeId, (byte)(flag));

    TreeOperationState parent = null;
    if(derived) {
      parent = rootOperation.iterator().next();
      parent.op.addDerived(moveOperation, parent.performed);
    }
    TreeOperationState tos = new TreeOperationState(moveOperation,false); 
    rootOperation.addFirst(tos);
    //TODO attributes change before

    oldId.put(uuid, oldNodeId);
    newId.put(uuid, newNodeId);

    tos.performed = true;
    
    addOperation(moveOperation);
    
    //TODO attributes change after
    
    rootOperation.remove(tos);
    // TODO is it needed?
//    if (oldId != null && !oldId.equals(newId)) {
////      getDeltaTarget()
////          .changed(
////              uuid,
////              AttributeStorage.formChangesSet(uuid, TreeNode.ATTR_ID, oldNodeId),
////              AttributeStorage.formAttributeDefMap(TreeNode.formIdAttribute(uuid, newNodeId)),
////              Moment.AFTER,
////              true);
//      getDeltaTarget().moved(uuid, getParent(uuid), getParent(uuid), Moment.AFTER, true);
//    }
  }

  @Override
  public void saveStorageAttributes(UUID uuid, ChangesSet<Attribute> changes, byte flag) {
    IStorageDeltaListener delt = getDeltaTarget();
    
    //delt.changed(uuid, changes, changes.getOldMap(), Moment.BEFORE, true);
    
    boolean derived = !rootOperation.isEmpty();    
    
    SaveAttributes saveOperation =
        new SaveAttributes(
            derived,
            uuid,
            changes, 
            (byte)(flag));

    TreeOperationState parent = null;
    if(derived) {
      parent = rootOperation.iterator().next();
      boolean error = false;
      if(parent.op instanceof DeleteNode && parent.performed)
        if(((DeleteNode)parent.op).getNodes().root.getUUId().equals(uuid))
          error = true;
      if(parent.op instanceof CreateNode && !parent.performed)
        if(((CreateNode)parent.op).getNodes().root.getUUId().equals(uuid))
          error = true;
      if(error)
        throw new RuntimeException("Tries to save attribute in incorrect state");
      parent.op.addDerived(saveOperation, parent.performed);
    }
    TreeOperationState tos = new TreeOperationState(saveOperation,false); 
    rootOperation.addFirst(tos);    
    //TODO attributes change before

    //delt.changed(uuid, changes, changes.getOldMap(), Moment.BEFORE, true);
    
    addOperation(saveOperation);
    Map<String, AttributeDefinition> ad = currentAttrs(uuid);
    if(ad!=null){
      Map<String, Attribute> newa = changes.getNewMap();
      for(Entry<String, Attribute> e:newa.entrySet())
      {
        if(e.getValue() == null)
          ad.remove(e.getKey());
        else
          ad.put(e.getKey(), e.getValue().getDefinition());
      }
    }
    tos.performed = true;
    //TODO attributes change after
    
    rootOperation.remove(tos);
    //delt.changed(uuid, changes, changes.getNewMap(), Moment.AFTER, true);
  }

  @Override
  public void moveNodeTo(UUID uuid, UUID newParent, byte flag) {
    IStorageDeltaListener delt = getDeltaTarget();
    
    if(newParent.equals(uuid))
      throw new RuntimeException("Error! Tried to move node to itself");

    boolean derived = !rootOperation.isEmpty();    
    
    UUID oldParent = getParent(uuid);
    MoveNode moveOperation = new MoveNode(derived, uuid, oldParent, newParent, (byte)(flag));

    TreeOperationState parent = null;
    if(derived) {
      parent = rootOperation.iterator().next();
      parent.op.addDerived(moveOperation, parent.performed);
    }
    TreeOperationState tos = new TreeOperationState(moveOperation,false); 
    if(!derived)
      rootOperation.addFirst(tos);
    delt.moved(uuid, oldParent, newParent, Moment.BEFORE, (byte)(flag));
    
    if (oldParent != null) currentChildren(oldParent).remove(uuid);
    currentChildren(newParent).add(uuid);

    tos.performed = true;
    
    addOperation(moveOperation);
    
    delt.moved(uuid, oldParent, newParent, Moment.AFTER, (byte)(flag));
    if(!derived)
      rootOperation.remove(tos);
  }

  private Collection<UUID> currentChildren(UUID uuid) {

    // TODO
    // Storage == null when mocking is active
    if (uuid == null && storage.getLocalName() != null) {
      throw new IllegalArgumentException("uuid is null");
    }
    if (!newChildren.containsKey(uuid)) {
      TreeSet<UUID> initalData = new TreeSet<>();
      Collection<UUID> children;
      // if(vstorage.canLoad(uuid))//vnode
      // children = vstorage.getChildren(uuid, true);
      // else
      children = storage.getChildren(uuid);
      if (children != null) {
        for (UUID uid : children) if (uid != null) initalData.add(uid);
      }
      newChildren.put(uuid, initalData);
      oldChildren.put(uuid, new TreeSet<>(initalData));
    }
    return newChildren.get(uuid);
  }

  private Map<String, AttributeDefinition> currentAttrs(UUID uuid) {
    if (!newAttr.containsKey(uuid)) {
      Map<String, AttributeDefinition> initalData = new HashMap<>();
      Map<String, AttributeDefinition> attrs = storage.getAttributes(uuid);
      if (attrs != null) {
        for (String s : attrs.keySet()) initalData.put(s, attrs.get(s));
      }
      newAttr.put(uuid, new HashMap<>(initalData));
      oldAttr.put(uuid, new HashMap<>(initalData));
    }
    return newAttr.get(uuid);
  }
  private static String OLD = "~~~old~~~|placeh";
  private Map<String, String> currentResources(UUID uuid) {
    if (!resourсes.containsKey(uuid)) {
      resourсes.put(uuid, new HashMap<String, String>());
      for (String s : storage.listResources(uuid))
        resourсes.get(uuid).put(s,OLD);
        // setResourceContents(uuid, s, null, null);
    }
    return resourсes.get(uuid);
  }
  
  public boolean isResourceInTransaction(UUID uuid, String resName) {
    Map<String, String> nodeResources = resourсes.get(uuid);
    if (nodeResources == null) {
      return false;
    }
    if (nodeResources.containsKey(resName)&&!OLD.equals(nodeResources.get(resName))) {
      return true;
    }
    return false;
  }

  // //////////////////////
  // Resources
  @Override
  public boolean isResourceExists(UUID uuid, String resName) {
    if (isResourceInTransaction(uuid, resName)) {
      if (resourсes.get(uuid).containsKey(resName) && resourсes.get(uuid).get(resName) == null) {
        return false;
      }
      return true;
    }
    // if (storage.getNodeId(uuid,true) == null)
    // return false;
    if (storage.isResourceExists(uuid, resName)) {
      if (!resourсes.containsKey(uuid)) { // we doun't change node in
        // transaction
        return true;
      }

      if (!resourсes.get(uuid).containsKey(resName)) { // we doun't change
        // resource in
        // transaction
        return true;
      }
      if (resourсes.get(uuid).get(resName) != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public InputStream getResourceContents(UUID uuid, String resName) {
    long t_start = System.currentTimeMillis();
    if (isResourceInTransaction(uuid, resName)) {
      try {
        if (resourсes.get(uuid).get(resName) != null) {
          if(OLD.equals(resourсes.get(uuid).get(resName)))
            return storage.getResourceContents(uuid, resName);
          return new FileInputStream(resourсes.get(uuid).get(resName));
        }
        return null;
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      }
    }

    if (verbose) {
      long t_end = System.currentTimeMillis();
      RLogger.logDebug(
          "time TransactionStorage.getResourceContents("
              + uuid
              + ", "
              + resName
              + ") = "
              + (t_end - t_start));
    }
    return storage.getResourceContents(uuid, resName);
  }

  @Override
  public void setResourceContents(UUID uuid, String resName, InputStream in, Object data, byte flag) {
    IStorageDeltaListener delt = getDeltaTarget();
    long t_start = System.currentTimeMillis();
    try {
      Map<String, String> nodeResources = currentResources(uuid);

      String oldContentFile = null;

      if (isResourceExists(uuid, resName)) {
        File tmpOldFile = File.createTempFile(TMP_RESOURCE_PREFIX, "old");
        tmpOldFile.deleteOnExit();
        oldContentFile = tmpOldFile.getAbsolutePath();
        InputStream oldIn = getResourceContents(uuid, resName);
        
        OutputStream out = new FileOutputStream(tmpOldFile);
        IOUtils.copy(oldIn, out);
        //OutputStream out = new FileOutputStream(tmpOldFile);
        //ChangeResource.copy(oldIn, out);
        out.close();
        oldIn.close();
      }

      File tmpNewFile = File.createTempFile(TMP_RESOURCE_PREFIX, "new");
      tmpNewFile.deleteOnExit();
      String newContentFile = tmpNewFile.getAbsolutePath();
      try(OutputStream out = new FileOutputStream(tmpNewFile)){
        if (in != null) {
          ChangeResource.copy(in, out);
        }
      }

      boolean derived = !rootOperation.isEmpty();
      
      ChangeResource operation =
          new ChangeResource(derived, uuid, resName, oldContentFile, newContentFile, data, (byte)(flag));

      TreeOperationState parent = null;
      if(derived) {
        parent = rootOperation.iterator().next();
        parent.op.addDerived(operation, parent.performed);
      }
      TreeOperationState tos = new TreeOperationState(operation,false); 
      rootOperation.addFirst(tos);
      
      if(delt!=null)delt.resourceChanged(uuid, resName, Moment.BEFORE, (byte)(flag));
      
      nodeResources.put(resName, newContentFile);  

      tos.performed = true;
      
      addOperation(operation);

      if(delt!=null)delt.resourceChanged(uuid, resName, Moment.AFTER, (byte)(flag));

      rootOperation.remove(tos);
      
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (verbose) {
      long t_end = System.currentTimeMillis();
      RLogger.logDebug(
          "time TransactionStorage.setResourceContents("
              + uuid
              + ", "
              + resName
              + ", ...) = "
              + (t_end - t_start));
    }
  }

  @Override
  public void deleteResource(UUID uuid, String resName, byte flag) {
    IStorageDeltaListener delt = getDeltaTarget();
    //TODO mb change?
    try {
      
      File tmpOldFile = File.createTempFile(TMP_RESOURCE_PREFIX, "new");
      tmpOldFile.deleteOnExit();
      OutputStream out = new FileOutputStream(tmpOldFile);
      String oldContentFile = tmpOldFile.getAbsolutePath();
      InputStream in = getResourceContents(uuid, resName);
      try{
        if (in != null) ChangeResource.copy(in, out);
      }
      finally{
        if(in!=null)
          in.close();
        out.close();
      }
      
      Map<String, String> nodeResources = currentResources(uuid);
      boolean derived = !rootOperation.isEmpty();
      
      ChangeResource operation = new ChangeResource(derived, uuid, resName, oldContentFile, null, null, (byte)(flag));
      
      TreeOperationState parent = null;
      if(derived) {
        parent = rootOperation.iterator().next();
        parent.op.addDerived(operation, parent.performed);
      }
      TreeOperationState tos = new TreeOperationState(operation,false); 
      rootOperation.addFirst(tos);
      
      if (delt != null) delt.resourceChanged(uuid, resName, Moment.BEFORE, (byte)(flag));
        
      
      nodeResources.put(resName, null);
      
      tos.performed = true;
  
      addOperation(operation);
      
      if (delt != null) delt.resourceChanged(uuid, resName, Moment.AFTER, (byte)(flag));

      rootOperation.remove(tos);
  
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
  public void apply() {
    apply(true);
  }
  public void apply(boolean addtoundo) {
    apply(storage, addtoundo);
  }

  public void apply(ITreeStorage aStorage, boolean addtoundo) {
    CacheStorage cs = aStorage.getCacheStorage();
    CacheTransactionDescription tr=cs.startCacheTransaction("Undo "+getVisibleName(),2);
    try {
      
      //logFile("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
      //logFile("\tCommit");
      IMonitorManager mm = API.getMonitorManager();
      // aStorage.startChangeRow();
      LinkedList<TreeOperation> opss = new LinkedList<>();
      IProgressMonitor m = mm.startMonitor(this, "Applying "+getVisibleName(), 10000, false);
      int ops = operationQueue.size(), i = 0;
      if (verbose) RLogger.logDebug(ops + " operations to be performed", 4);
      m.beginTask("Applying "+getVisibleName(), operationQueue.size());
      int max = ops;
      int realmax = max;
      double inc = 1;
      double val = 0, old = -1;
      int j = ops;
      RLogger.logDebug("Operations: "+operationQueue.toArray(new TreeOperation[0]), 4);
      Lock wl = storage.writeLock(null);
      try{
        while (j > 0) {
          LinkedList<TreeOperation> q = new LinkedList<>(operationQueue);
          j = 0;
          opss.addAll(operationQueue);
          operationQueue.clear();
          for (TreeOperation op : q) {
            if (!(op instanceof SaveVersions)) j++;
            i++;
            if (i % 10 == 0 && verbose)
              RLogger.logDebug(i + " operations out of " + opss.size() + " were completed", 4);
            if (verbose) {
              RLogger.logDebug("applying operation " + op, 4);
            }
            CacheTransactionDescription b = null;
            CacheStorage cs2 = storage.getCacheStorage();
            try{
              b = cs2.startCacheTransaction("Start db transaction",CacheStorage.COMMIT_SINGLE);
              applyOperation(op, aStorage);
              //op.apply(aStorage, aStorage.getDeltaTarget());
              if(b != null && b.error == null) cs2.commit(b);
            }
            catch(Exception e){
              if(b != null && b.error == null) cs2.rollback(b);
              throw e;
            }
            //operationsData.add(new OperationData(op, op.getData()));
            val+=inc;
            if(val-old>=1){
              m.worked(1);
              old = val;
            }
          }
        }
        operationQueue.addAll(opss);
        realmax+=opss.size();
        m.worked(max-realmax);
        inc = 0;
        if(realmax>0)
          inc = max/realmax;
        versstorage.clear();
        List<ITransactionListener> tmpListeners = new ArrayList<>(tListeners);
        for (ITransactionListener tl : tmpListeners) {
          if (execute && addtoundo) {
            tl.execute(TransactionStorage.this);
          } else {
            tl.redo(TransactionStorage.this);
          }
        }
      }finally{
        wl.unlock();
      }
      
      execute = false; // next time apply means redo
    }finally {
      if(tr!=null && tr.error == null)
        cs.commit(tr);
    }
    // aStorage.stopChangeRow();
  }

  private void applyOperation(TreeOperation op, ITreeStorage aStorage) {
	  //TODO look on
    for(TreeOperation to: op.getBeforeOperations())
      applyOperation(to, aStorage);
    op.apply(aStorage, storage.getDeltaTarget());
    for(TreeOperation to: op.getAfterOperations())
      applyOperation(to, aStorage);
  }
  
  private void rollbackOperation(TreeOperation op, ITreeStorage aStorage) {
	  //TODO look on
    List<TreeOperation> la = op.getAfterOperations();
    ListIterator<TreeOperation> li = la.listIterator(la.size());
    while(li.hasPrevious())
      rollbackOperation(li.previous(), aStorage);
    op.rollback(aStorage, aStorage.getDeltaTarget());
    la = op.getBeforeOperations();
    li = la.listIterator(la.size());
    while(li.hasPrevious())
      rollbackOperation(li.previous(), aStorage);
  }
  

  public void rollback() {
    rollback(storage, API.getMonitorManager());
  }

  public void rollback(ITreeStorage aStorage, IMonitorManager mm) {
    CacheStorage cs = aStorage.getCacheStorage();
    CacheTransactionDescription tr=cs.startCacheTransaction("Undo "+getVisibleName(),2);
    try {
      // aStorage.startChangeRow();
      LinkedList<TreeOperation> opss = new LinkedList<>();
      Lock wl = aStorage.writeLock(null);
      try{
        // TODO think
       // while(j>0){
          opss.addAll(operationQueue);
          Collections.reverse(opss);
          LinkedList<TreeOperation> q = new LinkedList<>(opss);
          operationQueue.clear();
            IProgressMonitor m = mm.startMonitor(this, "Revert "+getVisibleName(), q.size(), false);
              //Collections.reverse(q);
              //operations.addAll(opss);
              int ops = q.size(), i = 0;
              if (verbose) RLogger.logDebug(ops + " operations to be reverted");
              TreeOperation op2;
              m.beginTask("Revert "+getVisibleName(), ops);
              Iterator<TreeOperation> it = q.iterator();
              while (it.hasNext()) {
                TreeOperation op = it.next();
                op2 = op;
                i++;
                if (i % 10 == 0 && verbose)
                  RLogger.logDebug(i + " operations out of " + opss.size() + " were completed");
                if (verbose) {
                  RLogger.logDebug("applying operation " + op);
                }
                CacheTransactionDescription b = null;
                CacheStorage cs2 = aStorage.getCacheStorage();
                try{
                  b = cs2.startCacheTransaction("Init db rollback",CacheStorage.COMMIT_SINGLE);
                  operationQueue.add(op);
                  rollbackOperation(op, aStorage);
                  //op.rollback(aStorage, aStorage.getDeltaTarget());
                }catch(Exception e){
                  if(b!=null && b.error==null){
                    b.error("already rolled back");
                    cs2.rollback(b);
                  }
                  RLogger.logError(e);
                  throw e;
                }finally {
                  if(b!=null && b.error==null){ 
                    cs2.commit(b);
                  }
                }
                m.worked(1);
              }
          
      //  }
        // }
        if (!(aStorage instanceof TransactionStorage)) {
          List<ITransactionListener> tmpListeners = new ArrayList<>(tListeners);
          for (ITransactionListener tl : tmpListeners) {
            tl.undo(this);
          }
        }
        LinkedList<TreeOperation> ro = new LinkedList<>(operationQueue);
    
        Collections.reverse(ro);
        operationQueue.clear();
        operationQueue.addAll(ro);
      }
      finally
      {
        wl.unlock();
      }
    }finally {
      if(tr!=null && tr.error == null)
        cs.commit(tr);
    }
    // aStorage.stopChangeRow();
  }

  public void dispose() {
    for (TreeOperation op : operationQueue) {
      op.dispose();
    }
  }

  @Override
  public void createStorage(String lname,String pname) {
    // TODO Implement me!
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public void deleteStorage() {
    // TODO Implement me!
    throw new UnsupportedOperationException("not implemented");
  }

  public ITreeStorage getVStorage() {
    return vstorage;
  }

  @Override
  public ITreeStorage getBaseStorage() {
    return storage;
  }
  
  @Override
  public void clearNodeCache(UUID uid) {
    storage.clearNodeCache(uid);
  }

  @Override
  public void clearCaches(boolean del) {
    storage.clearCaches(del);
  }


  @Override
  public File getJSONResource(UUID uid, LockType lock) throws LockIsBusy {
    if(newAttr.containsKey(uid)) {
      //TODO implement with proper return
    }
    return storage.getJSONResource(uid, lock);
  }
  @Override
  public File getResource(UUID uuid, String resName) {
    long t_start = System.currentTimeMillis();
    if (isResourceInTransaction(uuid, resName)) {
      if (resourсes.get(uuid).get(resName) != null) {
        if(OLD.equals(resourсes.get(uuid).get(resName)))
          return storage.getResource(uuid, resName);
        return new File(resourсes.get(uuid).get(resName));
      }
      return null;
    }

    if (verbose) {
      long t_end = System.currentTimeMillis();
      RLogger.logDebug(
          "time TransactionStorage.getResourceContents("
              + uuid
              + ", "
              + resName
              + ") = "
              + (t_end - t_start));
    }
    return storage.getResource(uuid, resName);
  }

  @Override
  public URI getLocation() {
    return storage.getLocation();
  }

  @Override
  public Lock writeLock(UUID uid) {
    return storage.writeLock(uid);
  }

  @Override
  public void writeUnlock(UUID uid) {
    storage.writeUnlock(uid);
  }
  @Override
  public IProgressMonitor getMonitor() {
    if(storage!=null)
      return storage.getMonitor();
    return monitor;
  }

  @Override
  public Collection<IMark> getStorageNodeProblems(UUID uid){
    return storage.getStorageNodeProblems(uid);//TODO add more problems?
  }


}
