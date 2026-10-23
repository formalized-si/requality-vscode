package com.unitesk.requality.vscode.port.eclipse.handlers.copypaste;

import java.util.Collection;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;

public class CopyHandler extends AbstractHandler {
  com.unitesk.requality.core.operation.CopyHandler ch =
      new com.unitesk.requality.core.operation.CopyHandler();

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    Collection<TreeNode> allNodes = SelectionManager.getAllNodes(SelectionManager.getOne().getSelection(EventSource.EXPLORER));
    copyNodes(allNodes, false);
    return null;
  }

  public void copyNodes(Collection<TreeNode> nodes, boolean cutNode) {
    ch.copyNodes(nodes, cutNode);
  }

  @Override
  public boolean isEnabled() {
    Collection<TreeNode> nodes = SelectionManager.getAllNodes(SelectionManager.getOne().getSelection(EventSource.EXPLORER));
    return ch.isEnabled(nodes);
  }
}
