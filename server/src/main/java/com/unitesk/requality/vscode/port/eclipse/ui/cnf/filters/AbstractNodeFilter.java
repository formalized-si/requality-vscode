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
import com.unitesk.requality.core.model.TreeNode;

public abstract class AbstractNodeFilter extends ViewerFilter {

  public AbstractNodeFilter() {
    super();
  }

  protected String getSelectionType(Object element) {
    if (element instanceof TreeNode) {
      TreeNode node = (TreeNode) element;
      return node.getType();
    }
    return null;
  }

  @Override
  public boolean select(Viewer viewer, Object parentElement, Object element) {
    String type = getSelectionType(element);
    if (type == null) return true;
    for (String t : getFilterTypes()) {
      if (type.equals(t)) {
        return false;
      }
    }
    return true;
  }

  public abstract String[] getFilterTypes();
}
