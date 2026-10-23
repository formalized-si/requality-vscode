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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.Version;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.CoreUtils;

public class NodeDesc {
  // taken from transaction storage
  private final UUID initialuuid;
  private UUID uuid;
  private String suuid = null;
  private Map<String, AttributeDefinition> attributes;
  private Map<String, File> resources;
  private Map<UUID, NodeDesc> children;
  private List<UUID> childrenOrder = new LinkedList<>();
  private UUID parentUUId;
  private UUID showparentUUId;
  private TreeDB db;
  private Version version;
  private UUID cloneParent;

  private static Map<UUID, NodeDesc> listToMap(List<UUID> uids) {
    Map<UUID, NodeDesc> map = new TreeMap<>();
    for (UUID uid : uids) 
    	map.put(uid, null);
    return map;
  }
  
  private static void recForm(NodeDesc nd, LinkedList<NodeDesc>list){
    for(NodeDesc nda:nd.getChildren().values())
      recForm(nda, list);
    list.add(nd);
  }
  
  public static LinkedList<NodeDesc> getFullDescSet(NodeDesc nd){
    LinkedList<NodeDesc> fullDescSet = new LinkedList<>();
    recForm(nd, fullDescSet);
    return fullDescSet;
  }
  

  public NodeDesc(
      UUID uuid,
      UUID parentUUId,
      String id,
      Map<String, AttributeDefinition> map, 
      boolean removeVersion) {
    this(null, uuid, uuid, null, parentUUId, parentUUId, id, map, null ,null, removeVersion);
  }
  public NodeDesc(
      TreeDB db,
      UUID uuid,
      UUID cloneParent,
      UUID parentUUId,
      UUID showparentUUId,
      String id,
      Map<String, AttributeDefinition> map,
      List<UUID> uids,
      boolean removeVersion) {
    this(db, uuid, uuid, cloneParent, parentUUId, showparentUUId, id, map, listToMap(uids),uids, removeVersion);
  }

  public NodeDesc(
      TreeDB db,
      UUID initUUID,
      UUID uuid,
      UUID cloneParent,
      UUID parentUUId,
      UUID showparentUUID,
      String id,
      Map<String, AttributeDefinition> attributes,
      Map<UUID, NodeDesc> map,
      List<UUID> childOrder,
      boolean removeVersion) {
    this.uuid = uuid;
    initialuuid = initUUID==null?uuid:initUUID;
    suuid = null;
    this.db = db;
    this.attributes = attributes;
    if(removeVersion)
      attributes.remove(AttributesVersioningRule.ATTR_NODE_VERSION);
    AttributeDefinition cp = attributes.get(TreeNode.ATTR_CLONE_PARENT);
    if(cp!=null && cloneParent == null)
      this.cloneParent = 
        UUID.fromString(
            cp.getRawValue().getValue().toString());//TODO if it will be copied?
    if ("DBRoot".equals(getType())) {//ported from testDB
      id = "/";
      parentUUId = null;
    }
    if(id == null) {
      AttributeDefinition ida = attributes.get(TreeNode.ATTR_ID);
      if(ida!=null)
        id = (String)ida.getRawValue().getValue();
    }
    if(id!=null)
      setId(id);
    this.cloneParent = cloneParent;
    this.children = map;
    this.childrenOrder = childOrder;
    this.parentUUId = parentUUId;
    this.showparentUUId = showparentUUID;
  }

  @SuppressWarnings("unchecked")
  static Object renewList(ListValue value, List<Object> newValues) {
    if (value.getType() == AttributeType.LIST) {
      List<Object> k = new ArrayList<>();
      Iterator<?> i = newValues.iterator();
      for (Object o : value.getValues()) {
        Object o2 = i.next();
        k.add(renewList((ListValue) o, (List<Object>) o2));
      }
      return new ListValue(value.getType(), k);
    } else {
      return new ListValue(value.getType(), new ArrayList<>(newValues));
    }
  }

  public void setId(String id) {
    this.attributes.put(TreeNode.ATTR_ID, TreeNode.formIdAttribute(uuid, id));
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof NodeDesc)) {
      return false;
    }
    NodeDesc nodeDesc = (NodeDesc) obj;
    if (!nodeDesc.getVersion().equals(getVersion())) {
      return false;
    }
    if (!nodeDesc.getId().equals(getId())) {
      return false;
    }
    if (!nodeDesc.getUUId().equals(getUUId())) {
      return false;
    }
    if (!attrubuteEquals(nodeDesc)) {
      return false;
    }
    if (!childrenEquals(nodeDesc)) {
      return false;
    }
    return true;
  }

  public boolean equalsButUUID(Object obj) {
    if (!(obj instanceof NodeDesc)) {
      return false;
    }
    NodeDesc nodeDesc = (NodeDesc) obj;
    if (!nodeDesc.getId().equals(getId())) {
      return false;
    }
    if (!attrubuteEquals(nodeDesc)) {
      return false;
    }
    if (!childrenEquals(nodeDesc)) {
      return false;
    }
    return true;
  }

  private boolean childrenEquals(NodeDesc nodeDesc) {
    return getChildrenUUId().equals(nodeDesc.getChildrenUUId());
  }

  private boolean attrubuteEquals(NodeDesc nodeDesc) {
    return AttributeStorage.attributesDefsEquals(nodeDesc.getAttributes(), getAttributes());
  }

  public Version getVersion() {
    return version == null ? Version.getUnset() : version;
  }

  public String getId() {
    return attributes.get(TreeNode.ATTR_ID).getRawValue().getValue().toString();
  }

  public UUID getUUId() {
    return uuid;
  }

  public List<UUID> getChildrenUUId() {
    return Collections.unmodifiableList(new ArrayList<>(childrenOrder));
  }

  public Map<String, AttributeDefinition> getAttributes() {
    return attributes;
  }

  public void setAttribute(AttributeDefinition attr) {
    // List<Attribute> list = attributes.get(attr.getKey());
    // if(list==null)
    // list = new LinkedList<Attribute>();
    // list = AttributeStorage.addToList(attributes,list, attr, uuid);
    attributes.put(attr.getKey(), attr);
  }

  public AttributeDefinition removeAttribute(String key) {
    return attributes.remove(key);
    // List<Attribute> clist = attributes.get(key);
    // if(clist != null && !clist.isEmpty()){
    // Iterator<Attribute> i = clist.iterator();
    // Attribute a = i.next();
    // if(a.getOrigin().equals(uuid))
    // i.remove();
    // return a;
    // }
    // return null;
  }

  public UUID getWorkParentUUID() {
    return parentUUId;
  }

  public UUID getShownParentUUID() {
    return showparentUUId;
  }

  @Override
  public String toString() {
    return getId() + "[" + getType() + "]";
  };

  public String getType() {
    AttributeDefinition a = getAttributes().get(TreeNode.ATTR_TYPE);
    if (a != null) {
      TypedValue tv = ((ConstValueProvider)a.getValueProvider()).getRawValue();
      if (tv == null)
        return AttributeStorage.getCachedStorage(db, a.getOrigin())
            .getStringAttributeValue(a.getKey());
      return tv.getValue().toString();
      // }
    }
    return "TreeNode";
  }

  public void setChildrenDesc(UUID chUUId, NodeDesc chDesc) {
    chDesc.setParentUUId(uuid);
    children.put(chUUId, chDesc);
  }

  public NodeDesc getChildDesc(UUID chUUId) {
    return children.get(chUUId);
  }
  
  
  private NodeDesc recFullNodeDescs(NodeDesc nodeDesc, UUID newParent, Map<UUID, UUID> uuidsMap, Map<UUID, NodeDesc> appliedNodes) {
	// TODO Auto-generated method stub
    UUID newUuid = db.getUuidGenerator().nextUUId();

    Map<UUID, NodeDesc> newChi = nodeDesc.children==null?Collections.emptyMap():new HashMap<>(nodeDesc.children);
    List<UUID> newChiOrder = nodeDesc.childrenOrder==null?Collections.emptyList() : new LinkedList<>(nodeDesc.childrenOrder);
    NodeDesc nd = new NodeDesc(db, nodeDesc.initialuuid, newUuid, nodeDesc.getCloneParent(),
        newParent, newParent, nodeDesc.getId(), nodeDesc.getUpdatedAttributes(nodeDesc.uuid, newUuid),
        newChi, newChiOrder, false);
    uuidsMap.put(nodeDesc.uuid, newUuid);
    appliedNodes.put(newUuid, nd);
    
    nd.children = new HashMap<>();
    nd.childrenOrder = new LinkedList<>();
    for(UUID u:newChiOrder)
    {
      NodeDesc chi = newChi.get(u);
      if(chi == null)
    	  continue;
      NodeDesc chiRepl = recFullNodeDescs(chi, newUuid, uuidsMap, appliedNodes);      
      nd.childrenOrder.add(chiRepl.uuid);
      nd.children.put(chiRepl.uuid, chiRepl);
    }
    return nd;
  }

  public TreeNode createNode(TreeDB db, UUID pUUId, Boolean deep) {
    return createNode(db, pUUId, deep, false);
  }

  public TreeNode createNode(TreeDB db, UUID pUUId, Boolean deep, Boolean saveUUID) {
    // new node - new uuid
    Map<UUID, UUID> uuidsMap = new HashMap<>();
    Map<UUID, NodeDesc> appliedNodes = new HashMap<>();
    NodeDesc nodeRepl = this;
    if (!saveUUID)
      nodeRepl = recFullNodeDescs(this, pUUId, uuidsMap, appliedNodes);
      
    TreeNode result = db.createChildNode(NodeDescSet.fromNodeDesc(nodeRepl, deep), getType(), OFlag.NONE);

    if (resources != null)
      for (String s : resources.keySet())
        try {
          File f = resources.get(s);
          if (f.exists()) {
            FileInputStream fis = new FileInputStream(f);
            result.setResourceContent(s, fis, OFlag.NONE);
            fis.close();
          }
        } catch (FileNotFoundException e) {
          e.printStackTrace();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
    return result;
  }

  private Map<String, AttributeDefinition> getUpdatedAttributes(UUID uid, UUID uuid2) {
    Map<String, AttributeDefinition> natr = new HashMap<>();
    for(Entry<String, AttributeDefinition> s:attributes.entrySet()) {
      String key = s.getKey();
      AttributeDefinition ad = s.getValue();
      if(ad.getOrigin().equals(uid))//TODO copy all?
        natr.put(key, new AttributeDefinition(uuid2, key, ad.getAvailability(), ad.getValueProvider(), ad.getFlags()));
      else
        natr.put(key, ad);
    }
    return natr;
  }

  private UUID getCloneParent() {
    return cloneParent;
  }

  public TreeNode createNode(TreeDB db) {
    return createNode(db, false);
  }

  public TreeNode createNode(TreeDB db, Boolean saveLink) {
    return createNode(db, parentUUId, true, saveLink);
  }

  public void setParentUUId(UUID pUUId) {
    this.parentUUId = pUUId;
    showparentUUId = pUUId; // TODO
  }

  public static NodeDesc fromNode(TreeNode node) {
    return fromNode(node, true);
  }

  public static NodeDesc fromNode(
	      final TreeNode node, Boolean deep) {
    return fromNode(true, node, deep);
  }
  static NodeDesc fromNode( boolean firstRoot,
      final TreeNode node, Boolean deep) {
    List<TreeNode> chi = node.getSortedWorkChildren();//now we can copy hidden vnodes
    List<UUID> uids = new LinkedList<>();
    for(TreeNode nde:chi)
      uids.add(nde.getUUId());
    if (VirtualNode.TYPE_NAME.equals(node.getType())) uids = Collections.emptyList();
    Map<String, Attribute> attrs = node.attrs().getActiveAttributes();
    Map<String, Attribute> attributes = new HashMap<>(attrs);
    Map<String, AttributeDefinition> natr = new HashMap<>();
    for (Map.Entry<String, Attribute> entry : attributes.entrySet()) {
      String key = entry.getKey();
      if (Attribute.isNonCopyable(node.getTreeDB(), key, firstRoot))
        // if(key.equals("_%id"))
        continue; // TODO look into
      Attribute newa = null;
      Attribute attr = entry.getValue();
      if(attr!=null)
      {
       // UUID origin = attr.getOrigin();
        if (!attr.isLocal(node.getUUId()))
          attr = node.attrs().getAttribute(attr.getKey());
        if (attr == null || !attr.getOrigin().equals(node.getUUId())) continue;
       
        natr.put(key, new AttributeDefinition(node.getUUId(), attr.getKey(), attr.getAvailability(),
              attr.getDefinition().getValueProvider(),
              attr.getDefinition().getFlags()));
      }
      
    }
    NodeDesc result =
        new NodeDesc(
            node.getTreeDB(),
            node.getUUId(),
            node.getCloneParentUUId(),
            node.getWorkParentUUID(),
            node.getShownParentUUID(),
            node.getId(),
            natr,
            uids, true);//seems that in this case we always remove version
    Set<String> ress = node.listResources();
    result.resources = null;

    for (String s : ress) {
      InputStream is = node.getResourceContent(s);
      try{
        if (is == null) continue;
        byte[] bt;
        try {
          if(result.resources == null)
            result.resources = new HashMap<>();
          File tmpNewFile = createTempFile(result);
          bt = new byte[is.available()];
          is.read(bt);
          FileOutputStream fos = new FileOutputStream(tmpNewFile);
          fos.write(bt);
          fos.close();
          result.resources.put(s, tmpNewFile);
          tmpNewFile.deleteOnExit();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      finally{
        try {
          if(is!=null)
            is.close();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
    // node.getResourceContent(name)
    if (deep) {
      for (TreeNode child : chi) {
        //TreeNode child = node.getWorkChild(i, false, onlyloaded);
        if (child.isVirtual() && !node.isVirtual()) 
          RLogger.logDebug("not add");//TODO adds not virtual copy
        else 
          result.setChildrenDesc(child.getUUId(), NodeDesc.fromNode(false, child, deep));
      }
    }
    return result;
  }

  private static File createTempFile(NodeDesc result) throws IOException {
	if(result.resources == null)
      result.resources = new HashMap<>();
    File f = File.createTempFile(
                  CoreUtils.CACHE_FOLDER_NAME, result.getSUUId() + result.resources.size());
    f.deleteOnExit();
    return f;
  }

  public void setChildrenUUIDs(Collection<UUID> chi) {
    if(children == null)
      children = new HashMap<>();
    for (UUID uid : chi)
      if (!children.containsKey(uid))
        children.put(uid, null); // may cause error
  }

  public Map<UUID, NodeDesc> getChildren() {
    if(children == null)
      return Collections.emptyMap();
    return children;
  }

  public String getIndex() {
    if (getAttributes().containsKey(TreeDB.INDEX_ATTRIBUTE)) {
      AttributeDefinition a = getAttributes().get(TreeDB.INDEX_ATTRIBUTE);
      ;
      if (a.getRawValue()==null || a.getRawValue().getValue() == null)
        return AttributeStorage.getCachedStorage(db, a.getOrigin())
            .getStringAttributeValue(a.getKey());
      return a.getRawValue().getValue().toString();
    }
    return "0";
  }

  public String getSUUId() {
    if(suuid == null)
      suuid = uuid.toString();
    return suuid;
  }

  public void putResource(String s, File resource) {
    if(resources == null)
      resources = new HashMap<>();
    try {
		File tf = createTempFile(this);
		Files.copy(resource.toPath(), tf.toPath(), StandardCopyOption.REPLACE_EXISTING);
		resources.put(s, tf);
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
  }
  public Set<String> resourcesSet() {
    return resources==null?Collections.emptySet():resources.keySet();
  }
}
