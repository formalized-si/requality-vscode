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

import java.util.Map;
import java.util.UUID;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.Version;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public interface IStorageDeltaListener {
  ITreeStorage getStorage();
//  default void beforeCreated(NodeDesc desc, String template, boolean internal) {
//    beforeCreated(desc.getUUId(), desc.getWorkParentUUID(), template, desc.getAttributes(), internal);
//    for(NodeDesc chi:desc.getChildren().values())
//      beforeCreated(chi, null, internal);//TODO template?
//  }
  default void created(UUID uid, String template, byte flag) {
    ITreeStorage storage = getStorage();
    created(TreeDB.initNodeDescSet(storage, uid), template, flag);
  }
  void created(NodeDescSet nd, String template, byte flag);

  default void beforeCreated(UUID uuid, UUID par, String template, Map<String,AttributeDefinition> attributes, byte flag) {
    AttributeDefinition id = attributes == null ? null : attributes.get(TreeNode.ATTR_ID);
    String ids = id == null ? null : (String) id.getRawValue().getValue();
    beforeCreated(NodeDescSet.fromNodeDesc(new NodeDesc(uuid, par, ids, attributes, false),true), template, flag);
  }
  void beforeCreated(NodeDescSet desc, String template, byte flag);
    
  default void deleted(UUID uuid, UUID par, Map<String,AttributeDefinition> attributes, byte flag) {
    AttributeDefinition id = attributes == null ? null : attributes.get(TreeNode.ATTR_ID);
    String ids = id == null ? null : (String) id.getRawValue().getValue();
    deleted(NodeDescSet.fromNodeDesc(new NodeDesc(uuid, par, ids, attributes, false), true), flag);
  }
  void deleted(NodeDescSet nd, byte flag);
  
  default void beforeDeleted(UUID uid, byte flag){
    ITreeStorage storage = getStorage();
    beforeDeleted(TreeDB.initNodeDescSet(storage, uid), flag); 
  }
  
  void beforeDeleted(NodeDescSet desc, byte flag);
  

  //attrs are new on before stage and old on after
//  void changed(
//      UUID uuid, ChangesSet<Attribute> what, Map<String, AttributeDefinition> newa, Moment m, boolean forceInternal);

  void versionChanged(UUID uuid, ChangesSet<Version> changes, Moment m, byte flag);

  void resourceChanged(UUID uuid, String resName, Moment m, byte flag);

  void moved(UUID uuid, UUID oldParent, UUID newParent, Moment m, byte flag);

  void startChangeRow();

  void stopChangeRow();

  boolean changeAttributes(UUID uuid, ChangesSet<Attribute> what, byte flag);
  
  ITreeStorage operationStarted(String name, String opId);
  
  void operationEnded(String opId); 
}
