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
package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.views.RequalityContentProvider;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;

public class SelectTestPurposeDialog extends SelectNodeFromRequirementsTree {
  public static final String NAME = Consts.SELECT_TEST_PURPOSE_DIALOG_NAME;
  public SelectTestPurposeDialog(Shell shell, TreeDB treedb) {
    super(shell, treedb, false);
    setTypeOfCreatedChilden(TestPurpose.TYPE_NAME);
  }

  @Override
  public String getTitle() {
    return "Test Purpose selection";
  }

  @Override
  protected ITreeContentProvider getContentProvider() {
    RequalityContentProvider result = new RequalityContentProvider();
    result.addFilter(
        new ITreeNodeFilter() {
          @Override
          public boolean select(TreeNode node) {
            if (node.getType().equals(TestPurpose.TYPE_NAME)) return true;
            if (node.getType().equals(Requirement.TYPE_NAME)) {
              int n = node.sizeShownChildren();
              if (n == 0) return true;
              for (int i = 0; i < n; i++) if (select(node.getShownChild(i))) return true;
            }
            return false;
          }
        });
    return result;
  }

  @Override
  public boolean canSelectNode() {
    if (selectedNode == null) return false;
    if (!selectedNode.getType().equals(TestPurpose.TYPE_NAME)) return false;
    // TODO Auto-generated method stub
    return super.canSelectNode();
  }

  @Override
  public boolean canCreateNodes() {
    return true;
  }

  @Override
  public boolean canCreateNode() {
    if (selectedNode == null) return false;
    return db.getTreeLogic().canBeChildOf(TestPurpose.TYPE_NAME, selectedNode);
  }

  @Override
  protected String getRootQId() {
    return "/";
  }

  @Override
  protected String defaultIdPrefix() {
    if (selectedNode instanceof Requirement)
      return ((Requirement) selectedNode).getNameOrId() + "_T";
    return selectedNode.getId() + "_T";
  }

  @Override
  protected void saveAction(TreeNode n) {
    if (n.getType().equals(TestPurpose.TYPE_NAME)) {
      n.attrs().setConstAttribute(TestPurpose.ATTR_AUTHOR, System.getProperty("user.name", ""),Flag.HIDDEN);
      n.attrs().setConstAttribute(TestPurpose.ATTR_STATUS, "in process",Flag.HIDDEN);
    }
  }
}
