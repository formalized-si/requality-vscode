package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;

/** {@code org.eclipse.ui.IActionDelegate}: an action that is run and updated by the navigator. */
public interface IActionDelegate {
  void run(IAction action);

  void selectionChanged(IAction action, ISelection selection);
}
