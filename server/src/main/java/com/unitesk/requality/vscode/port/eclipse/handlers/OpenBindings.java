package com.unitesk.requality.vscode.port.eclipse.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.BindingsDialog;

public class OpenBindings extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
	SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    TreeNode nds = nodes.getFirstNode();
    if(nds == null)
      return null;
    BindingsDialog b = new BindingsDialog(EclipseAPI.getDisplay().getActiveShell(),nds);
    b.open();
    return null;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER); 
    if (nodes == null || nodes.fullSelection.size()!=1) 
      return false;
    return true;
  }
}
