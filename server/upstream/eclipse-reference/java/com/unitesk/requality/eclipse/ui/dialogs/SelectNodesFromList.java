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
package com.unitesk.requality.eclipse.ui.dialogs;

import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.views.RequalityContentProvider;

public class SelectNodesFromList extends SelectNodeFromRequirementsTree {
  List<TreeNode> nodearray;
  String caption;
  Button selectorcreate = null;
  private static String TEXT_OPEN_OR_CREATE = "Open or create Test Purpose";
  private final SelectionListener openOrCreateListener =
      new SelectionListener() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          SelectTestPurposeDialog dialog =
              new SelectTestPurposeDialog(SelectNodesFromList.this.getShell(), db);
          if ((dialog.open() == GetNodeIdDialog.OK) && (db != null)) {
            SelectNodesFromList.this.selectedNode = dialog.getResult();
            SelectNodesFromList.this.close();
          }
        }

        @Override
        public void widgetDefaultSelected(SelectionEvent e) {
          widgetDefaultSelected(e);
        }
      };

  @Override
  protected Control createDialogArea(Composite parent) {
    Control control = super.createDialogArea(parent);
    selectorcreate = new Button(header, SWT.PUSH);
    selectorcreate.setText(TEXT_OPEN_OR_CREATE);
    selectorcreate.addSelectionListener(openOrCreateListener);
    return control;
  }

  public SelectNodesFromList(List<TreeNode> nodes, Shell shell, TreeDB treedb, String caption) {
    super(shell, treedb, false);
    nodearray = nodes;
    this.caption = caption;
    if (caption.equals("")) caption = "Choose Nodes from list";
  }

  @Override
  public String getTitle() {
    return caption;
  }

  @Override
  protected ITreeContentProvider getContentProvider() {
    RequalityContentProvider result = new RequalityContentProvider();
    result.addFilter(
        new ITreeNodeFilter() {
          @Override
          public boolean select(TreeNode node) {
            if (nodearray.contains(node)) return true;
            int n = node.sizeShownChildren();
            if (n == 0) return false;
            for (int i = 0; i < n; i++) if (select(node.getShownChild(i))) return true;
            return false;
          }
        });
    return result;
  }

  @Override
  public boolean canSelectNode() {
    return nodearray.contains(selectedNode);
  }

  @Override
  public boolean canCreateNodes() {
    return false;
  }

  @Override
  public boolean canCreateNode() {
    return false;
  }

  @Override
  protected String getRootQId() {
    return "/";
  }
}
