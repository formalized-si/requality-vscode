package com.unitesk.requality.vscode.ui.workbench.actions;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetSelectionDialog;

/** {@code org.eclipse.ui.internal.actions.SelectWorkingSetAction}: "Select Working Set...": chooses the active working set. */
public class SelectWorkingSetAction extends Action {
  private final Shell shell;
  private final WorkingSetFilterActionGroup actionGroup;

  /**
   * @param actionGroup the group that holds the active working set
   * @param shell       the shell the dialog opens on
   */
  public SelectWorkingSetAction(WorkingSetFilterActionGroup actionGroup, Shell shell) {
    super("Select &Working Set...");
    Assert.isNotNull(actionGroup);
    setToolTipText("Select a working set");
    this.shell = shell;
    this.actionGroup = actionGroup;
  }

  @Override
  public void run() {
    IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
    IWorkingSetSelectionDialog dialog = manager.createWorkingSetSelectionDialog(shell, false);
    IWorkingSet workingSet = actionGroup.getWorkingSet();
    if (workingSet != null) {
      dialog.setSelection(new IWorkingSet[] { workingSet });
    }
    if (dialog.open() == Window.OK) {
      IWorkingSet[] result = dialog.getSelection();
      if (result != null && result.length > 0) {
        actionGroup.setWorkingSet(result[0]);
        manager.addRecentWorkingSet(result[0]);
      } else {
        actionGroup.setWorkingSet(null);
      }
    } else {
      actionGroup.setWorkingSet(workingSet);
    }
  }
}
