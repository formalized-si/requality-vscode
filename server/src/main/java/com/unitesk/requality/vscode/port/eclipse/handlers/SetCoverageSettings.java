package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.util.Collection;

import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.IHandler;
import com.unitesk.requality.vscode.ui.commands.IHandlerListener;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.CoverageReportSettingsDialog;
import com.unitesk.requality.nodetype.ReportSettings;

public class SetCoverageSettings implements IHandler {

  @Override
  public void addHandlerListener(IHandlerListener handlerListener) {
    // TODO Auto-generated method stub

  }

  @Override
  public void dispose() {
    // TODO Auto-generated method stub

  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    TreeNode node = (TreeNode) event.getParameters().get("target");
    Shell shell = (Shell) event.getParameters().get("shell");
    if(shell == null)
      shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
    CoverageReportSettingsDialog wiz =
        new CoverageReportSettingsDialog(
            shell, node);
    try {
      return wiz.open();
    } catch (Exception e) {
      RLogger.logError(e);
    }
    return null;
  }

  @Override
  public boolean isEnabled() {
    return isHandled();
  }

  @Override
  public boolean isHandled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null) return false;
    if (nodes.fullSelection.size() != 1) return false;
    Collection<TreeNode>nds = nodes.get(ReportSettings.TYPE_NAME);
    if(nds == null || nds.size()!=1) return false;
    TreeNode node = nodes.getFirstNode();
    if(node == null)
      return false;
    TreeDB db = node.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
      return false;
    String s = ((ReportSettings) node).getTemplateId();
    if (s == null) return false;
    return true;
  }

  @Override
  public void removeHandlerListener(IHandlerListener handlerListener) {
    // TODO Auto-generated method stub

  }
}
