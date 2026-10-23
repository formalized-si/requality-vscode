package com.unitesk.requality.vscode.ui.workbench.actions;

import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.workbench.IActionDelegate;
import com.unitesk.requality.vscode.ui.workbench.IObjectActionDelegate;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;

/** {@code org.eclipse.ui.actions.ActionDelegate}: the empty implementation of the action delegates. */
public abstract class ActionDelegate implements IObjectActionDelegate {
  @Override
  public void setActivePart(IAction action, IWorkbenchPart targetPart) {
  }

  @Override
  public void run(IAction action) {
  }

  @Override
  public void selectionChanged(IAction action, ISelection selection) {
  }
}
