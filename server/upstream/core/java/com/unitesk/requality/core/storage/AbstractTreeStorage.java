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
package com.unitesk.requality.core.storage;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.api.IAPICacheProvider;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.Version;
import com.unitesk.requality.core.attribute.VersionsStorage;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.storage.cache.ICacheStorageProvider;
import com.unitesk.requality.core.storage.cache.UUIDsCacheMan;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public abstract class AbstractTreeStorage implements ITreeStorage, ICacheStorageProvider {
  private IStorageDeltaListener deltaTarget;
  protected CacheStorage cs;
  //Here to be stored version between transactions
  //for Struct and Attrs to allow proper handling of undo-redo
  protected Map<UUID, Map<String, Version>> versstorage = new HashMap<>();
  protected Map<String, CacheStorage> caches = new HashMap<>();
  protected StorageCacheManipulatorsManager cmm = new StorageCacheManipulatorsManager();
  protected IProgressMonitor monitor = new ConsoleProgressMonitor();
  protected boolean resourcesOutdated = false;

  public StorageCacheManipulatorsManager getCMM(){
    return cmm;
  }
  private UUIDsCacheMan ucm = null;
  public boolean forceNow;
  public UUIDsCacheMan getUCM(){
    if(ucm == null)
      ucm = new UUIDsCacheMan(this);
    return ucm;
  }

  public void initCaches(ITreeStorage storage, CacheInitializationStage stage, IProgressMonitor mon) throws Exception{
	//it is local storage, all tends to be correct
    //caches = new HashMap<>();
    synchronized (caches) {
      for(String s:CacheStorage.getCacheDBNames(true))
        if(s.equals(IAPICacheProvider.DEFAULT)){
          if(cs == null)
            cs = new CacheStorage(s, true, storage, "");
//          else
//            RLogger.logError("reinit call for storage");
        }else if(!caches.containsKey(s))
          caches.put(s, new CacheStorage(s, true, storage, "_"+s));
        else
          RLogger.logError("reinit call for secondary storage");
	}
    CacheStorage tcs = cs;
    CacheTransactionDescription trans = tcs==null?null:tcs.startCacheTransaction("Init caches on tree storage "+storage, 2);
    try {
      getCMM().initCM(this, mon);
      URI loc = getLocation();
      if(loc!=null)
        getCMM().initCache(new File(loc).toPath(),getCMM().getSettingsStore(), stage, mon);
    }finally {
      if(tcs!=null && trans != null && trans.error == null)
        tcs.commit(trans);
    }
  }
  public CacheStorage getCacheStorage(String id){
    synchronized (this) {
      if(caches == null)
        throw new RuntimeException("Caches are not inited!");
    }
    return caches.get(id);
  }
  @Override
  public CacheStorage getCacheStorage(TreeDB db){
    synchronized (this) {
      if(caches == null)
        throw new RuntimeException("Caches are not inited!");
    }
    return getCacheStorage();
  }

  @Override
  public CacheStorage getCPCacheStorage(TreeDB dbfrom) {
    //normal cache storage knows nothing about cp
    return null;
  }
//  protected int chRow = 0;
//
//  public int startChangeRow() {
//    return chRow++;
//  }
//
//  public int stopChangeRow() {
//    return chRow--;
//  }
  public AbstractTreeStorage() {
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#setDeltaTarget(com.unitesk.requality.core.IStorageDeltaListener)
   */
  @Override
  public void setDeltaTarget(IStorageDeltaListener deltaTarget) {
    this.deltaTarget = deltaTarget;
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#getDeltaTarget()
   */
  @Override
  public IStorageDeltaListener getDeltaTarget() {
    return deltaTarget;
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#createNode(java.util.UUID, java.util.UUID, java.lang.String, java.lang.String)
   */
  @Deprecated
  @Override
  public void createNode(UUID parentNode, final UUID node, String type, String id, String template, byte flag) {
    if (parentNode == null) {
      type = "DBRoot";
    }
    final String ftype = type;
    Map<String, AttributeDefinition> attr = new HashMap<>();
    attr.put(
        TreeNode.ATTR_TYPE,
        new AttributeDefinition(node, TreeNode.ATTR_TYPE, new ConstValueProvider(AttributeType.STRING, ftype),
            Flag.HIDDEN));
    
    createNode(NodeDescSet.fromNodeDesc(new NodeDesc(node, parentNode, id, attr, false), true), template, flag);
    //createNode(parentNode, node, id,template, attr);
  }

  @Override
  public String getNodeRawType(UUID uuid) {
    return AttributeStorage.getNodeRawTypeByAD(uuid, getAttributes(uuid));
  }
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#getNodeType(java.util.UUID)
   */

  public Map<String, Version> getVersions(UUID uuid) {
    Map<String, Version> base = new HashMap<>(VersionsStorage.getDefaults()),
        stored = versstorage.get(uuid);
    if (stored == null) stored = Collections.<String, Version>emptyMap();
    for (String s : stored.keySet()) base.put(s, stored.get(s));
    return base;
  }

  public void saveVersions(UUID uuid, ChangesSet<Version> changes, byte flag) {
    if (getDeltaTarget() != null) getDeltaTarget().versionChanged(uuid, changes, Moment.BEFORE, flag);
    Map<String,Version> nmap = new HashMap<>(changes.getNewMap()); 
    versstorage.put(uuid, nmap);
    for (String s : nmap.keySet()) 
      nmap.put(s, nmap.get(s).clone());
    if (getDeltaTarget() != null) getDeltaTarget().versionChanged(uuid, changes, Moment.AFTER, flag);
  }

  public ITreeStorage getRootStorage() {
    ITreeStorage base = this;
    while (base.getBaseStorage() != null) base = base.getBaseStorage();
    return base;
  }

//  protected Map<UUID, Map<String, Map<String, Map<String,Object>>>> cacheStore =
//      new HashMap<>();


  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#getRootNode()
   */
  @Override
  public abstract UUID getRootNode();
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#getParent(java.util.UUID)
   */
  @Override
  public abstract UUID getParent(UUID uuid);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#getChildren(java.util.UUID)
   */
  @Override
  public abstract Collection<UUID> getChildren(UUID uuid);
  protected abstract void createStorageNode(
      NodeDescSet desc, String template, byte flag);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#createNode(java.util.UUID, java.util.UUID, java.lang.String, java.util.Collection)
   */
  @Override
  public final void createNode(
      NodeDescSet desc, String template, byte flag){
    //desc.setAttribute(TreeNode.formIdAttribute(desc.getUUId(), desc.getId()));
    createStorageNode(desc, template, flag);
  }
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#remove(java.util.UUID)
   */
  @Override
  public abstract void remove(NodeDescSet desc, byte flag);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#getNodeId(java.util.UUID)
   */
  @Override
  public abstract String getNodeId(UUID uuid);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#changeNodeId(java.util.UUID, java.lang.String)
   */
  @Override
  public abstract void changeNodeId(UUID uuid, String id, byte flag);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#getAttributes(java.util.UUID)
   */
  public Map<String, AttributeDefinition> getAttributes(UUID uuid){
    Map<String, AttributeDefinition> fatr = getStorageAttributes(uuid);
    if(fatr!=null && !fatr.containsKey(TreeNode.ATTR_ID))
      fatr.put(TreeNode.ATTR_ID,TreeNode.formIdAttribute(uuid, getNodeId(uuid)));
    return fatr;
  }
  public abstract Map<String, AttributeDefinition> getStorageAttributes(UUID uuid);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#saveAttributes(java.util.UUID, java.util.Collection)
   */
  @Override
  public void saveAttributes(
      UUID uuid, ChangesSet<Attribute> params, byte flag){
    Attribute id = params.getNew(TreeNode.ATTR_ID);
    if(id!=null)
      changeNodeId(uuid, id.getGValue(), flag);
    //TODO change resource for attribute-related moments here on the future
    saveStorageAttributes(uuid, params, flag);
  }
  public abstract void saveStorageAttributes(
      UUID uuid, ChangesSet<Attribute> params, byte flag);
  
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#moveNodeTo(java.util.UUID, java.util.UUID)
   */
  @Override
  public abstract void moveNodeTo(UUID uuid, UUID parent, byte flag);

  @Override
  public abstract boolean canLoad(UUID uuid);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#isResourceExists(java.util.UUID, java.lang.String)
   */
  @Override
  public abstract boolean isResourceExists(UUID uuid, String resName);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#getNodeResourcePath(java.util.UUID)
   */
  @Override
  public abstract URI getNodeResourcePath(UUID uuid);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#getResourceContents(java.util.UUID, java.lang.String)
   */
  @Override
  public abstract InputStream getResourceContents(UUID uuid, String resName);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#setResourceContents(java.util.UUID, java.lang.String, java.io.InputStream)
   */
  @Override
  public abstract void setResourceContents(UUID uuid, String resName, InputStream in, Object data, byte flag);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#listResources(java.util.UUID)
   */
  @Override
  public abstract Set<String> listResources(UUID uuid);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#deleteResource(java.util.UUID, java.lang.String)
   */
  @Override
  public abstract void deleteResource(UUID uuid, String resName, byte flag);

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#createStorage(java.lang.String)
   */
  @Override
  public abstract void createStorage(String lname, String pname);
  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#deleteStorage()
   */
  @Override
  public abstract void deleteStorage();

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.ITreeStorage#getName()
   */
  @Override
  public abstract String getLocalName();
  @Override
  public abstract String getProjectName();

  public Map<UUID, Map<String, Version>> getVersionsStorage() {
    return versstorage;
  }

  public void clearNodeCache(UUID uid) {
    //TODO crear
    versstorage.remove(uid);
  }

  public void clearCaches(boolean del) {
    //TODO def clear
    //URI locv = getLocation();
//    if(del && locv!=null)
//      CacheStorage.removeByPath(locv);
//    getCacheStorage().remove();
    URI loc = getLocation();
    if(loc!=null){
      getCMM().clearCaches(new File(loc).toPath(), getCMM().getSettingsManipulator());
      getCMM().clearCMMCaches();
    }
    versstorage.clear();
//    java.nio.file.Path p = CoreUtils.getCacheDir(this);
//    if(p!=null)
//      if(del){
//        CoreUtils.delete(p.toFile(), true, false);
//      }else
//        CoreUtils.delete(p.toFile(), true, true);
  }

  
  public CacheStorage getCacheStorage()
  {
    return cs;
  }
  
  public Pair<String, ITreeStorage> startOperation(String name){
    IStorageDeltaListener l = getDeltaTarget();
    if(l != null){
      String opid = UUID.randomUUID().toString();
      ITreeStorage s = l.operationStarted(name, opid);
      return new Pair<>(opid, s);
    }
    return null;
  }
  public String endOperation(String opId){
    IStorageDeltaListener l = getDeltaTarget();
    if(l != null){
      l.operationEnded(opId);
      return null;
    }
    return null;
  }
  public void setMonitor(IProgressMonitor mon) {
    monitor = mon;
  }
  public IProgressMonitor getMonitor() {
    if(monitor == null)
      monitor = new ConsoleProgressMonitor();
    return monitor;
  }
  public URI getExternalNodeResourcePath(TreeDB db, UUID uuid) {
    return ResourcesProvider.getOne().getResourcesURI(db, uuid);
  }

  @Override
  public void markResourcesOutOfSync() {
    resourcesOutdated = true;
  }
  @Override
  public void markResourcesSync() {
    resourcesOutdated = false;
  }
  public boolean getResourcesOutOfSync() {
    return resourcesOutdated;
  }
  @Override
  public void renew() {
    if(monitor.isCanceled())
      monitor = new ConsoleProgressMonitor();//TODO more changes?
    for(Entry<String, CacheStorage> en:caches.entrySet())
      if(en.getValue().isDisconnected())
        en.getValue().renew();//TODO another?
    cs.renew();
    //TODO more monitor classes?
  }
}
