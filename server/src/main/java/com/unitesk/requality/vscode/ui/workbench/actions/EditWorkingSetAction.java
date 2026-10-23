package com.unitesk.requality.vscode.ui.workbench.actions;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetEditWizard;

/** {@code org.eclipse.ui.internal.actions.EditWorkingSetAction}: "Edit Active Working Set...". */
public class EditWorkingSetAction extends Action {
  private final Shell shell;
  private final WorkingSetFilterActionGroup actionGroup;

  /**
   * @param actionGroup the group that holds the active working set
   * @param shell       the shell the wizard opens on
   */
  public EditWorkingSetAction(WorkingSetFilterActionGroup actionGroup, Shell shell) {
    super("&Edit Active Working Set...");
    Assert.isNotNull(actionGroup);
    setToolTipText("Edit the active working set");
    this.shell = shell;
    this.actionGroup = actionGroup;
  }

  @Override
  public void run() {
    IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
    IWorkingSet workingSet = actionGroup.getWorkingSet();
    if (workingSet == null) {
      setEnabled(false);
      return;
    }
    IWorkingSetEditWizard wizard = manager.createWorkingSetEditWizard(workingSet);
    if (wizard == null) {
      MessageDialog.openError(shell, "Edit Working Set", "Can not edit the active working set.");
      return;
    }
    WizardDialog dialog = new WizardDialog(shell, wizard);
    dialog.create();
    if (dialog.open() == Window.OK) {
      actionGroup.setWorkingSet(wizard.getSelection());
    }
  }
}
