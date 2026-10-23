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

package com.unitesk.requality.eclipse.handlers;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IDeleteHandler;
import com.unitesk.requality.core.operation.delete.DeleteNodeOperation;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.eclipse.Activator;

public class DeleteSelection extends AbstractHandler implements IHandler, IElementUpdater {
  //maybe move to TreeLogic
  private DeleteNodeOperation baseDelete = new DeleteNodeOperation();
  private boolean canc = false;

  
  @SuppressWarnings("rawtypes")
  @Override
  public void updateElement(UIElement element, Map parameters) {
    Collection<TreeNode> nds = SelectionManager.getAllNodes(SelectionManager.getOne().getSelection(EventSource.EXPLORER));
    element.setText(getLabel(nds.toArray(new TreeNode[nds.size()])));
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    Collection<TreeNode> nds = SelectionManager.getAllNodes(SelectionManager.getOne().getSelection(EventSource.EXPLORER));
    deleteNodes(nds, false);
    return null;
  }

  public static String getLabel(TreeNode[] nodes) {
    if (nodes == null || nodes.length < 1) return "Delete Selection";
    boolean adds = nodes.length != 1;
    TreeNode nd = nodes[0];
    boolean sim = true;
    for (TreeNode node : nodes)
      if (!node.getType().equals(nd.getType())) {
        return "Delete Selection";
      }
    SortedMap<String, IDeleteHandler> dhlist = Activator.getDefault().getDeleteHandlers();
    String overrideTitle = null;
    Iterator<String> hit = dhlist.keySet().iterator();
    while (hit.hasNext()) {
      String dhname = hit.next();
      IDeleteHandler idh = dhlist.get(dhname);
      String msg = idh.titleOverride(nodes);
      if (msg != null) overrideTitle = msg;
    }
    String name = overrideTitle;
    if (name == null)
      name =
          sim ? (adds ? nd.getMultiTypeLabel() : nd.getTypeLabel()) : "Element" + (adds ? "s" : "");
    if (name.equals("Selection")) return "Delete Selection";
    return "Delete " + name;
  }
  private static WorkspaceJob wj = null;
  public void deleteNodes(Collection<TreeNode> snodes, boolean ignoreMessage) throws ExecutionException {
    if(wj != null)
      return;
    wj = new WorkspaceJob("Delete nodes: "+Arrays.toString(snodes.toArray())){
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        try {
          if(DeleteNodeOperation.deleteNodes(snodes, ignoreMessage))
            return Status.OK_STATUS;
          return Status.CANCEL_STATUS;
        }finally {
          wj = null;
        }
      }
    };
    wj.schedule();
  }

  boolean recEnabled = false;

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() < 1) return false;
    //TreeDB db = nodes.get(0).getTreeDB();
    //String t1 = nodes.get(0).getType();
    int cnt = 0;
    for (TreeNode node : nodes.fullSelection) {
      if (!node.getTreeDB().getTreeLogic().canBeDeleted(node)) {
        cnt++;
        continue;
      }
    }
    if (cnt == nodes.fullSelection.size()) return false;
    return super.isEnabled();
  }
}
