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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.tool.CoreUtils;

public class MemoryStorage extends AbstractTreeStorage {
  private UUID root = null;
  private Map<UUID, String> toId = new HashMap<>();
  private Map<UUID, Collection<UUID>> toChildren = new HashMap<>();
  private Map<UUID, Map<String, AttributeDefinition>> toAttributes =
      new HashMap<>();
  private Map<UUID, HashMap<String, ByteArrayOutputStream>> toResourсes =
      new LinkedHashMap<>();
  private Map<String, File> tempResFiles = new ConcurrentHashMap<>();
  private String localName = null;
  private String projectName = null;

  public MemoryStorage() {
    super();
    root = UUID.randomUUID();
    toId.put(root, "/");
    toChildren.put(root, new HashSet<>());
    toAttributes.put(root, AttributeStorage.formTypeMap(root, "DBRoot"));
    init(root);
  }

  public MemoryStorage(UUID root) {
    init(root);
  }
  private void init(UUID root){
    this.root = root;
    try {
      initCaches(this, CacheInitializationStage.STORAGE_INIT, getMonitor());
    } catch (Exception e) {
      RLogger.logError(e);
	}
  }

  public void emulateDeltaCreate(
      UUID parentUUId, UUID uuid, String type, String id, String template, Map<String, AttributeDefinition> attributes, byte flag) {
    attributes.put(TreeNode.ATTR_ID, TreeNode.formIdAttribute(uuid, id));
    getDeltaTarget().beforeCreated(uuid, parentUUId, template,attributes, flag);
    toId.put(uuid, id);
    toChildren.put(uuid, new HashSet<UUID>());
    toAttributes.put(uuid, attributes);
    if (parentUUId == null) {
      root = uuid;
    } else {
      toChildren.get(parentUUId).add(uuid);
    }
    getDeltaTarget().created(uuid, template, flag);
  }

  public void emulateDeltaDelete(UUID uuid, byte flag) {
    getDeltaTarget().beforeDeleted(uuid, flag);
    String id = toId.remove(uuid);
    Map<String, AttributeDefinition> attrs = toAttributes.remove(uuid);
    Collection<UUID> childrens = toChildren.remove(uuid);
    UUID realPar = null;
    for (UUID puuid : toChildren.keySet()) {
      if (toChildren.get(puuid).contains(uuid)) {
        toChildren.get(puuid).remove(uuid);
        realPar = puuid;
      }
    }
    for (UUID cuuid : childrens) {
      emulateDeltaDelete(cuuid, flag);
    }
    getDeltaTarget().deleted(uuid,realPar,attrs, flag);
  }

  public void emulateDeltaMove(UUID uuid, UUID newParent, byte flag) {
    UUID oldParent = null;
    for (UUID puuid : toChildren.keySet()) {
      if (toChildren.get(puuid).contains(uuid)) {
        oldParent = puuid;
      }
    }
    assert oldParent != null;
    getDeltaTarget().moved(uuid, oldParent, newParent, Moment.BEFORE, flag);
    toChildren.get(oldParent).remove(uuid);
    toChildren.get(newParent).add(uuid);
    getDeltaTarget().moved(uuid, oldParent, newParent, Moment.AFTER, flag);
  }

  public void emulateDeltaChanged(UUID uuid, ChangesSet<Attribute> what, Map<String, AttributeDefinition> attributes) {
//    getDeltaTarget().changed(uuid, what, attributes, Moment.BEFORE, false);
    toAttributes.put(uuid, attributes);
//    getDeltaTarget().changed(uuid, what, attributes, Moment.AFTER, false);
  }

  @Override
  public UUID getRootNode() {
    return root;
  }

  @Override
  public String getNodeId(UUID uuid) {
    return toId.get(uuid);
  }

  @Override
  public void createStorageNode(NodeDescSet ndb, String template, byte flag) {
    IStorageDeltaListener dnt = getDeltaTarget();
    if (dnt != null) dnt.beforeCreated(ndb, template, flag);
    List<NodeDesc> nds = ndb.allNodesCreate;
    ListIterator<NodeDesc> la = nds.listIterator();
    //TODO parallel?
    while(la.hasNext()) {
      NodeDesc nd = la.next();
      UUID parentUUId = nd.getWorkParentUUID();
      UUID uuid = nd.getUUId();
      String id = nd.getId();
      Map<String, AttributeDefinition> attrs = nd.getAttributes();
      toId.put(uuid, id);
      if (!toChildren.containsKey(uuid)) toChildren.put(uuid, new HashSet<UUID>());
      if (attrs == null) {
        attrs = new HashMap<>();
      }
      toAttributes.put(uuid, new HashMap<>(attrs));
      if (parentUUId == null) {
        root = uuid;
      } else {
        toChildren.get(parentUUId).add(uuid);
      }
    }
    if(dnt!=null) dnt.created(ndb, template, flag);
  }

  @Override
  public void remove(NodeDescSet ndb, byte flag) {
    IStorageDeltaListener dnt = getDeltaTarget();
    if(dnt!=null)
      dnt.beforeDeleted(ndb, flag);
    List<NodeDesc> nds = ndb.allNodesRemove;
    for(NodeDesc nd:nds) {
      UUID uuid = nd.getUUId();
      UUID par = nd.getWorkParentUUID();
      toId.remove(uuid);
      toAttributes.remove(uuid);
      if(par!=null) 
        toChildren.getOrDefault(par, Collections.emptySet()).remove(uuid);
      toChildren.remove(uuid);
    }
    if(dnt!=null)
      dnt.deleted(ndb, flag);
  }

  @Override
  public Map<String, AttributeDefinition> getStorageAttributes(UUID uuid) {
    Map<String, AttributeDefinition> m = toAttributes.get(uuid);
    return m;
  }

  @Override
  public Collection<UUID> getChildren(UUID uuid) {
    if (toChildren.get(uuid) == null) {
      return null;
    }
    return Collections.unmodifiableCollection(toChildren.get(uuid));
  }

  @Override
  public UUID getParent(UUID uuid) {
    for (UUID i : toChildren.keySet()) {
      if (toChildren.get(i).contains(uuid)) {
        return i;
      }
    }
    return null;
  }

  @Override
  public boolean canLoad(UUID uuid) {
    return toId.containsKey(uuid);
  }

  @Override
  public void changeNodeId(UUID uuid, String id, byte flag) {
    String old = getNodeId(uuid);
//    getDeltaTarget()
//        .changed(
//            uuid,
//            new ChangesSet<>(Collections.emptySet(),AttributeStorage.formSet(TreeNode.ATTR_ID),Collections.emptySet(),
//                AttributeStorage.formAttributeDefMap(TreeNode.formIdAttribute(uuid, old))),
//            AttributeStorage.formAttributeDefMap(TreeNode.formIdAttribute(uuid, id)),
//            Moment.BEFORE,
//            true);
//    getDeltaTarget().moved(uuid, getParent(uuid), getParent(uuid), Moment.BEFORE, true);
    toId.put(uuid, id);
//    getDeltaTarget()
//        .changed(
//            uuid,
//            new ChangesSet<>(Collections.emptySet(),AttributeStorage.formSet(TreeNode.ATTR_ID),Collections.emptySet(),
//                AttributeStorage.formAttributeDefMap(TreeNode.formIdAttribute(uuid, old))),
//            AttributeStorage.formAttributeDefMap(TreeNode.formIdAttribute(uuid, id)),
//            Moment.AFTER,
//            true);
//    getDeltaTarget().moved(uuid, getParent(uuid), getParent(uuid), Moment.AFTER, true);
  }

  @Override
  public void moveNodeTo(UUID node, UUID parent, byte flag) {
    UUID oldParent = getParent(node);
    getDeltaTarget().moved(node, oldParent, parent, Moment.BEFORE, flag);
    if (oldParent != null) toChildren.get(oldParent).remove(node);
    toChildren.get(parent).add(node);
    getDeltaTarget().moved(node, oldParent, parent, Moment.AFTER, flag);
  }

  @Override
  public void saveStorageAttributes(UUID uuid, ChangesSet<Attribute> changes, byte flag) {
//    getDeltaTarget().changed(uuid, changes, attributes, Moment.BEFORE, true);
    //Map<String, Attribute> old = getAttributes(uuid);

    //Map<String, Attribute> oldMap = getAttributes(uuid);
    if(!toAttributes.containsKey(uuid))
      toAttributes.put(uuid, new HashMap<String,AttributeDefinition>());
    AttributeStorage.updateMap(toAttributes.get(uuid),changes);
    //toAttributes.put(uuid, new HashMap(attributes));
//    for(String s:oldMap.keySet())
//      if(!changes.contains(s))
//        toAttributes.get(uuid).put(s,oldMap.get(s));
//    getDeltaTarget().changed(uuid, changes, attributes, Moment.AFTER, true);
  }

  @Override
  public URI getNodeResourcePath(UUID uuId) {
    return null;
    // throw new UnsupportedOperationException();
  }

  @Override
  public InputStream getResourceContents(UUID uuid, String resName) {
    // TODO check stream
    byte[] data = getResourceData(uuid, resName);
    if (data == null) {
      return null;
    }
    return new ByteArrayInputStream(data);
  }


  @Override
  public void setResourceContents(UUID uuid, String resName, InputStream in, Object data, byte flag) {
    if (!toResourсes.containsKey(uuid)) {
      toResourсes.put(uuid, new HashMap<String, ByteArrayOutputStream>());
    }
    getDeltaTarget().resourceChanged(uuid, resName, Moment.BEFORE, flag);
    Map<String, ByteArrayOutputStream> r = toResourсes.get(uuid);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    r.put(resName, out);
    byte[] buffer = new byte[1024];
    int len;
    try {
      while ((len = in.read(buffer)) != -1) {
        out.write(buffer, 0, len);
      }
      getDeltaTarget().resourceChanged(uuid, resName, Moment.AFTER, flag);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private byte[] getResourceData(UUID uuid, String resName) {
    if (!isResourceExists(uuid, resName)) {
      return null;
    }
    Map<String, ByteArrayOutputStream> nodeResources = toResourсes.get(uuid);
    ByteArrayOutputStream resData = nodeResources.get(resName);
    return resData.toByteArray();
  }

  @Override
  public boolean isResourceExists(UUID uuid, String resName) {
    Map<String, ByteArrayOutputStream> nodeResources = toResourсes.get(uuid);
    if (nodeResources == null) {
      return false;
    }
    ByteArrayOutputStream resData = nodeResources.get(resName);
    if (resData == null) {
      return false;
    }
    return true;
  }

  @Override
  public void deleteResource(UUID uuid, String resName, byte flag) {
    getDeltaTarget().resourceChanged(uuid, resName, Moment.BEFORE, flag);
    toResourсes.get(uuid).remove(resName);
    getDeltaTarget().resourceChanged(uuid, resName, Moment.AFTER, flag);
  }

  @Override
  public Set<String> listResources(UUID uuid) {
    if (!toResourсes.containsKey(uuid)) return new HashSet<>();
    return new HashSet<>(toResourсes.get(uuid).keySet());
  }

  @Override
  public void createStorage(String lname,String pname) {
    localName = lname;
    projectName = pname;
  }

  @Override
  public void deleteStorage() {}

  @Override
  public String getLocalName() {
    return localName;
  }
  @Override
  public String getProjectName() {
    return projectName;
  }
  @Override
  public String getVisibleName() {
  	return projectName;
  }

  @Override
  public ITreeStorage getBaseStorage() {
    // TODO Auto-generated method stub
    return null;
  }


  @Override
  public File getJSONResource(UUID uid, LockType lock) {
    throw new UnsupportedOperationException();
  }
  @Override
  public File getResource(UUID uuid, String name) {
    Map<String, ByteArrayOutputStream> nodeResources = toResourсes.get(uuid);
    if (nodeResources == null) {
      return null;
    }
    ByteArrayOutputStream resData = nodeResources.get(name);
    if (resData == null) {
      return null;
    }
    String tempFileName = uuid+"|"+name;
    File tf = tempResFiles.get(tempFileName);
    if(tf == null) {
      try {
		tf = Files.createTempFile("requalityMemoryFile", null).toFile();
	    tf.deleteOnExit();
	    Files.write(tf.toPath(), resData.toByteArray(), StandardOpenOption.TRUNCATE_EXISTING);
	    tempResFiles.put(tempFileName, tf);
	  } catch (IOException e) {
	  }
    }
    return tf;
  }

  @Override
  public URI getLocation() {
    URI ur = CoreUtils.getTempDir().resolve("CacheStorage"+root).toUri();
    new File(ur).deleteOnExit();
    return ur;
    //throw new UnsupportedOperationException();
  }

  ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
  //final LockProxy<ReentrantLock> rl = new LockProxy<>(new ReentrantLock());

  private ConcurrentMap<UUID, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();
  //final LockProxy<ReentrantLock> rl = new LockProxy<>(new ReentrantLock());
  
  public void writeUnlock(UUID uid){
    if(uid == null)
      rwl.writeLock().unlock();
    else{
      getRWL(uid).writeLock().unlock();
    }
  }
  public Lock writeLock(UUID uid){
    if(uid == null) {
      Lock l = rwl.writeLock();
      l.lock();
      return l;
    }else{
      Lock l = getRWL(uid).writeLock();
      l.lock();
      return l;
    }
      
  }
  private ReentrantReadWriteLock getRWL(UUID uid) {
    ReentrantReadWriteLock rl = locks.get(uid);
    if(rl == null){
      rl = new ReentrantReadWriteLock();
      locks.put(uid, rl); 
    }
    return rl;
  }

  @Override
  public Collection<IMark> getStorageNodeProblems(UUID uid){
    return Collections.emptyList();
  }

}
