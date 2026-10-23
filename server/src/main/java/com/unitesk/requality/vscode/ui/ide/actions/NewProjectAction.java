package com.unitesk.requality.vscode.ui.ide.actions;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.dialogs.NewWizard;

/**
 * {@code org.eclipse.ui.actions.NewProjectAction}: opens the new wizard selection dialog with the project wizards only
 * (the "Project..." item of the New menu).
 */
public class NewProjectAction extends Action {
  private final IWorkbenchWindow window;

  public NewProjectAction(IWorkbenchWindow window) {
    super("P&roject...");
    if (window == null)
      throw new IllegalArgumentException();
    this.window = window;
    setToolTipText("New Project");
  }

  @Override
  public void run() {
    IWorkbench workbench = PlatformUI.getWorkbench();
    NewWizard wizard = new NewWizard();
    wizard.setProjectsOnly(true);
    ISelection selection = window.getSelectionService().getSelection();
    IStructuredSelection selectionToPass = StructuredSelection.EMPTY;
    if (selection instanceof IStructuredSelection)
      selectionToPass = (IStructuredSelection) selection;
    wizard.init(workbench, selectionToPass);
    new WizardDialog(window.getShell(), wizard).open();
  }
}
