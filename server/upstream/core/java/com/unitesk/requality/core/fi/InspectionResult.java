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
package com.unitesk.requality.core.fi;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;

public class InspectionResult extends TreeNode implements IInspectionResult {

  public static final String TYPE_NAME = "InspectionResult";
  public static String getVisibleType() {
    return "Inspection result";
  }

  public InspectionResult() {
    super(null, null);
  }

  public InspectionResult(TreeDB db, UUID uuid) {
    super(db, uuid);
  }

  @Override
  public String toString() {
    return getNameOrId();
  }

  @Override
  public List<InspectionStep> getSteps() {
    List<InspectionStep> ret = new LinkedList<>();
    List<TreeNode> childs = getSortedShownChildren(InspectionStep.TYPE_NAME);
    for(TreeNode node:childs)
      ret.add((InspectionStep) node);
    return ret;
  }

}
