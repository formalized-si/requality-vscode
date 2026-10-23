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
package com.unitesk.requality.vscode.port.eclipse.ui.cnf.filters;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.jface.viewers.TreePath;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.natures.ReqCatalogue;

public class NotRequalityDataFilter extends ViewerFilter {

  public NotRequalityDataFilter() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public boolean select(Viewer viewer, Object parentElement, Object element) {
    IProject project = null;
    if (parentElement instanceof TreePath) {
      TreePath tp = (TreePath) parentElement;
      if (tp.getLastSegment() instanceof IProject) {
        project = (IProject) tp.getLastSegment();
      }
    } else if (parentElement instanceof IProject) {
      project = (IProject) parentElement;
    }
    if (project == null) {
      return true;
    }
    try {
      if (project.getNature(ReqCatalogue.ID) == null) {
        return true;
      }
    } catch (CoreException e) {
      e.printStackTrace();
    }
    if (!(element instanceof TreeNode)) {
      return false;
    }
    return true;
  }
}
