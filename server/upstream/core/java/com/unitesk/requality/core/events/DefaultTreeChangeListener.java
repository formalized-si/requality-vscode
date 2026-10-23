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
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public class DefaultTreeChangeListener extends TreeChangeListener {
  @Override
  public void created(TreeNode node) {}

  @Override
  public void changed(TreeNode node, ChangesSet<Attribute> what) {}

  @Override
  public void deleted(TreeNode node) {}

  @Override
  public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent) {}

  @SuppressWarnings("unchecked")
  @Override
  public void changed(List<TreeNodeChangeEvent> changes) {
    for (TreeNodeChangeEvent ce : changes) {
      byte flag = ce.getFlag();
      switch (ce.getType()) {
        case CREATE:
          if(ce.getMoment() == Moment.AFTER)
            created(ce.getTreeNodeTarget(), flag);
          break;
        case DELETE:
          {
            deleted(ce.getTreeNodeTarget(), flag, ce.getMoment());
            break;
          }
        case CHANGE:
          changed(
            ce.getTreeNodeTarget(),
            (ChangesSet<Attribute>) ce.getArguments()[0],
            ce.getMoment(),
            flag);
          break;
        case MOVE:
          moved(
            ce.getTreeNodeTarget(),
            (TreeNode) ce.getArguments()[0],
            (TreeNode) ce.getArguments()[1],
            ce.getMoment(), flag);
          break;
        case CHILDREN_ORDER_CHANGE:
          childrenOrderChanged(
              ce.getTreeNodeTarget(), flag);
          break;
        default:
          break;
      }
    }
  }

  @Override
  public void childrenOrderChanged(TreeNode node, byte flag) {
    // TODO Auto-generated method stub
    
  }
}
