package com.unitesk.requality.vscode.ui.workbench.actions;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.runtime.Assert;

/** {@code org.eclipse.ui.internal.actions.ClearWorkingSetAction}: "Deselect Working Set": shows everything again. */
public class ClearWorkingSetAction extends Action {
  private final WorkingSetFilterActionGroup actionGroup;

  /** @param actionGroup the group that holds the active working set */
  public ClearWorkingSetAction(WorkingSetFilterActionGroup actionGroup) {
    super("Deselect Wor&king Set");
    Assert.isNotNull(actionGroup);
    setToolTipText("Deselect the active working set");
    setEnabled(actionGroup.getWorkingSet() != null);
    this.actionGroup = actionGroup;
  }

  @Override
  public void run() {
    actionGroup.setWorkingSet(null);
  }
}
