package com.unitesk.requality.vscode.ui.workbench.actions;

import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;

/** {@code org.eclipse.ui.actions.ActionGroup}: actions contributed to a context menu and to action bars. */
public abstract class ActionGroup {
  private ActionContext context;

  public ActionContext getContext() {
    return context;
  }

  public void setContext(ActionContext context) {
    this.context = context;
  }

  public void fillContextMenu(IMenuManager menu) {
  }

  public void fillActionBars(IActionBars actionBars) {
  }

  public void updateActionBars() {
  }

  public void dispose() {
  }
}
