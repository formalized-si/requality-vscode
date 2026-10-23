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
package com.unitesk.requality.eclipse.ui.cnf;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.ui.navigator.CommonDropAdapter;
import org.eclipse.ui.navigator.CommonDropAdapterAssistant;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.DropNodeOperation;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeStateManager;

public class CNFDropAssistant extends CommonDropAdapterAssistant {
  private List<TreeNode> srcNodes;

  public CNFDropAssistant() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public IStatus validateDrop(Object target, int operation, TransferData transferType) {
    if (!isSupportedType(transferType)) {
      return Status.CANCEL_STATUS;
    }
    TreeStateManager tsm = TreeStateManager.getOne(ResManager.getManager().getTracker());
    if (target instanceof TreeNode) {
      TreeDB db = ((TreeNode)target).getTreeDB();
      if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
        return Status.CANCEL_STATUS;
      LocalSelectionTransfer transfer = LocalSelectionTransfer.getTransfer();
      ITreeSelection selection = (ITreeSelection) transfer.getSelection();
      if (selection == null) {
        return Status.CANCEL_STATUS;
      }
      srcNodes = new ArrayList<>();
      Object[] select = selection.toArray();
      for (Object sel : select) {
        if (sel instanceof TreeNode) {
          TreeNode node = (TreeNode)sel;
          if(db==null || db!=node.getTreeDB()) {
          	db = node.getTreeDB();
          	if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
          		return Status.CANCEL_STATUS;
          }
          srcNodes.add(node);
        } else {
          return Status.CANCEL_STATUS;
        }
      }
      if (select.length == 1 && select[0] == target) return Status.CANCEL_STATUS;
      return Status.OK_STATUS;
    }
    return Status.CANCEL_STATUS;
  }

  @Override
  public IStatus handleDrop(
      CommonDropAdapter aDropAdapter, DropTargetEvent aDropTargetEvent, Object aTarget) {
    if (!(aTarget instanceof TreeNode)) {
      return Status.CANCEL_STATUS;
    }
    return execute(srcNodes, (TreeNode)aTarget, (TreeNode)aTarget);
  }
  public static IStatus execute(List<TreeNode> srcNodes,TreeNode parent, TreeNode aTarget){
    if (srcNodes == null || aTarget == null) {
      return Status.CANCEL_STATUS;
    }
    final boolean alt = RequalityCNF.altPressed;
    final boolean shift = RequalityCNF.shiftPressed;
    String mod = "to ";
    String op = "Drop node ";
    if(shift)
      mod = "after ";
    if(alt)
      op = "Reuse node ";
    WorkspaceJob wj = new WorkspaceJob(op+mod+aTarget.getUserVisibleName()){
        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
          if(srcNodes.size()<1)
            return Status.CANCEL_STATUS;
          TreeDB db = srcNodes.iterator().next().getTreeDB();
          if(DropNodeOperation.execute(db, srcNodes, aTarget, alt, shift))
            return Status.OK_STATUS;
          return Status.CANCEL_STATUS;
        }
	};
	wj.schedule();
	return Status.CANCEL_STATUS;
  }
}
