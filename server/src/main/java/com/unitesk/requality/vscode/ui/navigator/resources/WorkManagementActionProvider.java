package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.window.IShellProvider;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionContext;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory;
import com.unitesk.requality.vscode.ui.workbench.actions.AddBookmarkAction;
import com.unitesk.requality.vscode.ui.workbench.actions.AddTaskAction;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.WorkManagementActionProvider}: supports the Add Task and
 * Add Bookmark actions of the explorer, as the global action handlers of the view.
 */
public class WorkManagementActionProvider extends CommonActionProvider {
  private AddTaskAction addTaskAction;
  private AddBookmarkAction addBookmarkAction;

  @Override
  public void init(ICommonActionExtensionSite aSite) {
    IShellProvider sp = () -> aSite.getViewSite() == null ? null : aSite.getViewSite().getShell();
    addBookmarkAction = new AddBookmarkAction(sp, true);
    addTaskAction = new AddTaskAction(sp);
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    super.fillActionBars(actionBars);
    updateActionBars();
    actionBars.setGlobalActionHandler(ActionFactory.BOOKMARK.getId(), addBookmarkAction);
    actionBars.setGlobalActionHandler(ActionFactory.ADD_TASK.getId(), addTaskAction);
  }

  @Override
  public void setContext(ActionContext context) {
    super.setContext(context);
    updateActionBars();
  }

  /** The actions follow the selection of the context (the view's selection changes without a new context). */
  @Override
  public void updateActionBars() {
    if (getContext() == null)
      return; // the actions keep the selection they were last given
    IStructuredSelection sel = getContext().getSelection() instanceof IStructuredSelection
        ? (IStructuredSelection) getContext().getSelection() : StructuredSelection.EMPTY;
    addBookmarkAction.selectionChanged(sel);
    addTaskAction.selectionChanged(sel);
  }
}
