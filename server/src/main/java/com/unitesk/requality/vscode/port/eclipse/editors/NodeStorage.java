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

package com.unitesk.requality.vscode.port.eclipse.editors;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.core.model.TreeNode;

public class NodeStorage implements IStorage {
  private TreeNode node = null;

  private InputStream content = new ByteArrayInputStream(new byte[] {});

  public NodeStorage(Object node) {
    this.node = (TreeNode) node;
  }

  public TreeNode getNode() {
    return node;
  }

  public InputStream getContents() throws CoreException {
    return content;
  }

  public IPath getFullPath() {
    return null;
  }

  public String getName() {
    return node!=null?node.getId():"null";
  }

  public boolean isReadOnly() {
    return false;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public Object getAdapter(Class adapter) {
    return null;
  }
}
