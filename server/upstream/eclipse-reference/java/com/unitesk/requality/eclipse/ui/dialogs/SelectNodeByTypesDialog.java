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

import java.util.Set;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.views.RequalityContentProvider;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public class SelectNodeByTypesDialog extends SelectNodeDialog {
  public SelectNodeByTypesDialog(Shell shell, TreeDB treedb) {
    super(shell, treedb, false);
  }

  @Override
  public String getTitle() {
    return "Requirement selection";
  }

  private Set<String> types;

  public SelectNodeByTypesDialog(Shell shell, TreeDB treedb, Set<String> set) {
    super(shell, treedb, false);
    this.types = set;
  }

  @Override
  protected ITreeContentProvider getContentProvider() {
    RequalityContentProvider result = new RequalityContentProvider(false);
    result.addFilter(
        new ITreeNodeFilter() {
          @Override
          public boolean select(TreeNode node) {
            return types.contains(node.getType());
          }
        });
    return result;
  }

  @Override
  protected ILabelProvider getLabelProvider() {
    return new LabelProvider() {
      @Override
      public Image getImage(Object element) {
        if (element instanceof Requirement) {
          Requirement req = (Requirement) element;
          if (req.getQualifiedId().equals("/Requirements")) {
            return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider()
                .getImage(EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProject(req.getTreeDB()));
          }
        }
        return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider().getImage(element);
      }

      @Override
      public String getText(Object element) {
        if (element instanceof Requirement) {
          Requirement req = (Requirement) element;
          if (req.getQualifiedId().equals("/Requirements")) {
            if (req.isVirtual())
              req =
                  (Requirement)
                      Instantiator.getRootTree(req.getTreeDB())
                          .getNode(req.getQualifiedId(), Path.QID);
            try {
              return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider()
                  .getText(EclipseTracker.getResourceByNode(req, LockType.NO_LOCK).getProject());
            } catch (LockIsBusy e) {
              RLogger.logDebug(CoreUtils.getTrace(e),7);
            }
          }
        }
        return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider().getText(element);
      }
    };
  }

  @Override
  public boolean canCreateNodes() {
    return true;
  }

  @Override
  public boolean canCreateNode() {
    if (!super.canCreateNode()) return false;
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
