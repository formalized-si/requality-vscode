package com.unitesk.requality.vscode.port.eclipse.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;

public class HideEditMenu extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    return null;
  }
  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() != 1) {
      return false;
    }
    TreeNode node = nodes.getFirstNode();
    if(node == null)
      return false;
    //TODO hide
    return true;
  }
}
