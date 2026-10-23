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

package com.unitesk.requality.eclipse.editors;

import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;

public class NodeEditorInput implements IStorageEditorInput {
  private NodeStorage storage = null, selstorage = null;
  private Object data = null;
  private ImageDescriptor image;
  private String eid = null;
  private FSId fsid = null;

  public NodeEditorInput(Object node, Object selection, String eid, FSId fsid) {
    storage = new NodeStorage(node);
    if ((selection != null) && (selection != node)) selstorage = new NodeStorage(selection);
    image = PlatformUI.getWorkbench().getEditorRegistry().getImageDescriptor(getName());
    this.fsid = fsid;
    this.eid = eid;
  };
  public NodeEditorInput(Object node, String eid, FSId editorId) {
    this(node, null, eid, editorId);
  };

  public String getEditorId(){
    return eid;
  }

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }

  public boolean exists() {
    return false;
  }

  public ImageDescriptor getImageDescriptor() {
    return image;
  }

  public String getName() {
    return storage.getName();
  }

  public IPersistableElement getPersistable() {
    return null;
  }

  public String getToolTipText() {
    return storage.getNode().getUserVisibleName();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public Object getAdapter(Class adapter) {
    return null;
  }

  public NodeStorage getStorage() throws CoreException {
    return storage;
  }

  public TreeNode getSelectedNode() throws CoreException {
    SelectionRecord sr = SelectionManager.getOne().getSelection(fsid);
    if(sr!=null)
    {
      if(sr.fullSelection.size()>0)
        return sr.getFirstNode();
    }
    if(selstorage!=null)
    {
      TreeNode node = selstorage.getNode();
      if(node!=null)
        return node;
    }
    return storage.getNode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof NodeEditorInput)) {
      return false;
    }
    NodeEditorInput input = (NodeEditorInput) obj;
    if (storage.getNode() == null && input.storage.getNode() != null) {
      return false;
    } else if (storage.getNode() != null && !storage.getNode().equals(input.storage.getNode())) {
      return false;
    }
    try {
      Object node = storage.getNode();
      IStorage storage2 = input.getStorage();
      Object otherNode = ((NodeStorage) storage2).getNode();
      if ((node==null ^ otherNode==null)||node!=null && !node.equals(otherNode)) {
        return false;
      }
    } catch (CoreException e) {
      e.printStackTrace();
      return false;
    }
    if(this.eid == null)
      return input.eid == null;
    if(input.eid == null)
      return false;
    if (!this.eid.equals(input.eid)) {
      return false;
    }
    return true;
  }
  public FSId getFSId() {
    return fsid;
  }
}
