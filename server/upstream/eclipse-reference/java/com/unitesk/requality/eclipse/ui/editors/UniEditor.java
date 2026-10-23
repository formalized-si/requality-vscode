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
package com.unitesk.requality.eclipse.ui.editors;

import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.attribute.valueprovider.ChildIndexProvider;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.service.ResourcesServlet;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;

public class UniEditor extends JSEditor {
  public static Set<String> defaultAttrs = new HashSet<>();
  static{
    defaultAttrs.add(TreeNode.ATTR_ID);
    defaultAttrs.add(TreeNode.ATTR_TYPE);
    defaultAttrs.add(TreeNode.ATTR_DESCRIPTION);
    defaultAttrs.add(Requirement.ATTR_LOCATIONS);
    defaultAttrs.add(TreeNode.ATTR_HIDDEN);
    defaultAttrs.add(TreeNode.ATTR_IGNORE_NAME);
    defaultAttrs.add(TreeNode.ATTR_NAME);
    defaultAttrs.add(Requirement.ATTR_STATUS);
    defaultAttrs.add(TestPurpose.ATTR_EXPECTED_RESULTS);
    defaultAttrs.add(TestPurpose.ATTR_STATUS);
    defaultAttrs.add(TextNode.ATTR_TNTYPE);
    defaultAttrs.add(ChildIndexProvider.ATTR_KEY);//for tp editor
    //defaultAttrs.add(TestPurpose.);
  }
  public static final String ID = Consts.UniEditorID;
  
  public UniEditor() {
    super(ResourcesServlet.UNIEDITOR,defaultAttrs);
    setTabLabel("UniEditor");
  }

  @Override
  public String getEditorKindId() {
    return ID;
  }

}
