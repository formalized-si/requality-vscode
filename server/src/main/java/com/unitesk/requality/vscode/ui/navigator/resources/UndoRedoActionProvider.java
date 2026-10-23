package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionContext;
import com.unitesk.requality.vscode.ui.workbench.operations.UndoRedoActionGroup;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.UndoRedoActionProvider}: Undo and Redo of the workspace
 * undo context (the workbench context: resource and node operations) as global actions of the navigator.
 */
public class UndoRedoActionProvider extends CommonActionProvider {
  private UndoRedoActionGroup undoRedoGroup;

  @Override
  public void init(ICommonActionExtensionSite anActionSite) {
    undoRedoGroup = new UndoRedoActionGroup(
        com.unitesk.requality.vscode.services.ExplorerService.navigator().getSite(),
        WorkspaceUndoUtil.getWorkspaceUndoContext(), true);
  }

  @Override
  public void dispose() {
    undoRedoGroup.dispose();
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    undoRedoGroup.fillActionBars(actionBars);
  }

  @Override
  public void fillContextMenu(IMenuManager menu) {
    undoRedoGroup.fillContextMenu(menu);
  }

  @Override
  public void setContext(ActionContext context) {
    undoRedoGroup.setContext(context);
  }

  @Override
  public void updateActionBars() {
    undoRedoGroup.updateActionBars();
  }
}
