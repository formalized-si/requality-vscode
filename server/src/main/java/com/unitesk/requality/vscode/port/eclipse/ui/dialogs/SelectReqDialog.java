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
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.views.RequalityContentProvider;
import com.unitesk.requality.nodetype.Requirement;

public class SelectReqDialog extends SelectNodeFromRequirementsTree {
  public SelectReqDialog(Shell shell, TreeDB treedb, boolean ignoretrans) {
    super(shell, treedb, ignoretrans);
  }

  @Override
  public String getTitle() {
    return "Requirement selection";
  }

  @Override
  protected ITreeContentProvider getContentProvider() {
    RequalityContentProvider result = new RequalityContentProvider();
    result.addFilter(
        new ITreeNodeFilter() {
          @Override
          public boolean select(TreeNode node) {
            return node.getType().equals(Requirement.TYPE_NAME);
          }
        });
    return result;
  }

  @Override
  public boolean canCreateNodes() {
    return true;
  }

  @Override
  public boolean canCreateNode() {
    if (selectedNode == null) return false;
    if (selectedNode.getType().equals(Requirement.TYPE_NAME))
      return selectedNode
          .getTreeDB()
          .getTreeLogic()
          .canBeChildOf(Requirement.TYPE_NAME, selectedNode);
    return false;
  }

  @Override
  protected String getRootQId() {
    return "/";
  }
}
