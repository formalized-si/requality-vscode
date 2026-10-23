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
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;

public class InstancesFolder extends TreeNode {
  public static final String TYPE_NAME = "InstancesFolder";
  public static final String ATTR_DISPLAY_STATUS = "_dstatus";

  public InstancesFolder() {
    super(null, null);
  }

  public InstancesFolder(TreeDB db, UUID uuid) {
    super(db, uuid);
  }

  @Override
  public String toString() {
    return getId();
  }

  public int getDisplayStatus() {
    return attrs().getIntAttributeValue(InstancesFolder.ATTR_DISPLAY_STATUS, 0);
  }

  public void setDisplayStatus(int value) {
    attrs().setConstAttribute(ATTR_DISPLAY_STATUS, value, Flag.HIDDEN);
    commitAttributes();
  }
}
