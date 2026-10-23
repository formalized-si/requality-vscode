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

import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.model.TreeNode;

public abstract class ComplexNodeFilter extends ViewerFilter {

  public ComplexNodeFilter() {
    super();
  }

  protected TreeNode getSelectedNode(Object element) {
    if (element instanceof TreeNode) {
      return (TreeNode) element;
    }
    return null;
  }

  @Override
  public boolean select(Viewer viewer, Object parentElement, Object element) {
    TreeNode node = getSelectedNode(element);
    if (node == null) return true;
    if (getFilter().select(node)) return true;
    return false;
  }

  public abstract ITreeNodeFilter getFilter();
}
