package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;

import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IDeleteHandler;
import com.unitesk.requality.nodetype.InstancesFolder;
import com.unitesk.requality.nodetype.VirtualNode;

public class DeleteVirtualNodeHandler extends AbstractHandler implements IDeleteHandler {

  public void recUpdateProcessed(TreeNode node, List<UUID> toprocess) {
    toprocess.add(node.getUUId());
    for (UUID uid : node.getWorkChildrenUUIDs()) {
      try {
        TreeNode nde = node.getTreeDB().getNode(uid);
        recUpdateProcessed(nde, toprocess);
      } catch (NodeNotFoundException e) {
      }
    }
  }

  @Override
  public boolean isSuitableFor(TreeNode tn) {
    return tn.getType().equals(VirtualNode.TYPE_NAME)
        || tn.getType().equals(InstancesFolder.TYPE_NAME);
  }

  @Override
  public void process(TreeNode[] nodes, List<UUID> processed, byte flag)
      throws ExecutionException {
    for (TreeNode node : nodes) {
      if (node == null) continue;
      if (processed.contains(node.getUUId())) continue;
      if (!node.getTreeDB().getTreeLogic().canBeDeleted(node)) continue;
      if (!isSuitableFor(node)) continue;
      List<UUID> toprocess = new ArrayList<>();
      recUpdateProcessed(node, toprocess);
      node.delete();
      processed.addAll(toprocess);
    }
  }

  @Override
  public String[] getWarnings(TreeNode[] nodes) {
    return null;
  }

  @Override
  public String titleOverride(TreeNode[] nodes) {
    return null;
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    return null;
  }
}
