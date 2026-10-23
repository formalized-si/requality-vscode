package com.unitesk.requality.vscode.ui.compare;

import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.workbench.IActionDelegate;

/**
 * {@code org.eclipse.compare.internal.BaseCompareAction}: a compare action that is enabled for the selection it is
 * given and runs on the selection of its last update.
 */
public abstract class BaseCompareAction implements IActionDelegate {
  private ISelection fSelection;

  @Override
  public final void run(IAction action) {
    run(fSelection);
  }

  @Override
  public final void selectionChanged(IAction action, ISelection selection) {
    fSelection = selection;
    if (action != null)
      action.setEnabled(isEnabled(fSelection));
  }

  /** Whether the action applies to the selection. */
  protected boolean isEnabled(ISelection selection) {
    return false;
  }

  /** Runs the action on the selection. */
  protected abstract void run(ISelection selection);
}
