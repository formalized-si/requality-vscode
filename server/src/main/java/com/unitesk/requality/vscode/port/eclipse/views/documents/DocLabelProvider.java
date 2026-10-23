/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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

package com.unitesk.requality.vscode.port.eclipse.views.documents;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.ide.IDE;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;

public class DocLabelProvider extends LabelProvider {
  @Override
  public String getText(Object element) {
    if (element instanceof TreeNode) {
      TreeNode node = (TreeNode) element;
      if (node.getQualifiedId().equals(Document.getTypeRootQId())) {
        IProject prj = EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProject(node.getTreeDB());
        if (prj != null) return EclipseTracker.getProjectName(prj);
      }
    }

    if (element instanceof Document) {
      Document doc = (Document) element;
      return doc.getId();
    }

    if (element instanceof DocFolder) {
      DocFolder f = (DocFolder) element;
      return f.getId();
    }
    if (element instanceof Location) {
      Location loc = (Location) element;
      if (loc.getDescription().length() > 20) {
        return loc.getDescription().substring(0, 20) + "...";
      } else {
        return loc.getDescription();
      }
    }
    return super.getText(element);
  }

  @Override
  public Image getImage(Object element) {
    if (element instanceof TreeNode) {
      TreeNode node = (TreeNode) element;
      if (node.getQualifiedId().equals(Document.getTypeRootQId())) {
        return PlatformUI.getWorkbench()
            .getSharedImages()
            .getImage(IDE.SharedImages.IMG_OBJ_PROJECT);
      }
    }
    if (element instanceof Document) {
      return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FILE);
    }
    if (element instanceof DocFolder) {
      return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FOLDER);
    }
    if (element instanceof Location) {
      return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_TOOL_UP);
    }
    return super.getImage(element);
  }
}
