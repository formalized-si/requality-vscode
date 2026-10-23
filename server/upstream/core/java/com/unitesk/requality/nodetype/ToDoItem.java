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
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;

/** Class representing an item using in tasks list after update processor */
public class ToDoItem extends TreeNode {

  public static final String TYPE_NAME = "ToDoItem";

  public static final String ATTR_REQ_LINK = "correspondingReq";
  public static final String ATTR_TRANSF_ACTION = "_transferAction";
  public static final String ATTR_STATUS = "_itemStatus";

  public static final String OLD_DOC = "_oldDoc";
  public static final String NEW_DOC = "_newDoc";

  public ToDoItem() {
    super(null, null);
  }

  public ToDoItem(TreeDB db, UUID uuid) {
    super(db, uuid);
  }

  @Override
  public String toString() {
    return getId();
  }

  @Override
  public boolean isRestricted() {
    return true;
  }
}
