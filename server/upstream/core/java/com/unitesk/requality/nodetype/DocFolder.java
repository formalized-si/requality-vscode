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
package com.unitesk.requality.nodetype;

import java.util.UUID;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public class DocFolder extends TreeNode implements INodeChangeListener {

  public static final String TYPE_NAME = "DocFolder";
  public static final String ID_PREFIX = "new_folder_";

  public static String getVisibleType() {
    return "Document Folder";
  }

  public DocFolder() {
    super(null, null);
  }

  public DocFolder(TreeDB db, UUID uuid) {
    super(db, uuid);
  }

  @Override
  public String toString() {
    return getId();
  }

  @Override
  public void attributeChange(TreeNode source, String what, Attribute old) {
    // TODO Auto-generated method stub

  }

  @Override
  public void deleted(TreeNode source) {
    // TODO Auto-generated method stub

  }

  @Override
  public void moved(TreeNode source, TreeNode oldParent) {}

  @Override
  public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
    // TODO Auto-generated method stub

  }

  @Override
  public void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m) {
    // TODO Auto-generated method stub

  }
}
