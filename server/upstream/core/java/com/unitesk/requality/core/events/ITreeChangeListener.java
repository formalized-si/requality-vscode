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

package com.unitesk.requality.core.events;

import java.util.List;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public interface ITreeChangeListener {
  void changed(List<TreeNodeChangeEvent> changes);

  void changed(TreeNode node, ChangesSet<Attribute> params, Moment m, byte flag);

  void beforeCreated(NodeDesc node, byte flag);
  
  void created(TreeNode node, byte flag);

  void childrenOrderChanged(TreeNode node, byte flag);

  void deleted(TreeNode nodeDesc, byte flag, Moment m);

  void moved(TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag);
}
