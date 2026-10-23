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
 * {@code org.eclipse.ui.actions.NewExampleAction}: opens the new wizard selection dialog on the examples category (the
 * "Example..." item of the New menu, shown only when the examples category exists).
 */
public class NewExampleAction extends Action {
  /** The category of the examples wizards ({@code WizardsRegistryReader.FULL_EXAMPLES_WIZARD_CATEGORY}). */
  public static final String FULL_EXAMPLES_WIZARD_CATEGORY = "org.eclipse.ui.Examples";

  private final IWorkbenchWindow window;

  public NewExampleAction(IWorkbenchWindow window) {
    super("E&xample...");
    if (window == null)
      throw new IllegalArgumentException();
    this.window = window;
    setToolTipText("New Example");
  }

  @Override
  public void run() {
    IWorkbench workbench = PlatformUI.getWorkbench();
    NewWizard wizard = new NewWizard();
    wizard.setCategoryId(FULL_EXAMPLES_WIZARD_CATEGORY);
    ISelection selection = window.getSelectionService().getSelection();
    IStructuredSelection selectionToPass = StructuredSelection.EMPTY;
    if (selection instanceof IStructuredSelection)
      selectionToPass = (IStructuredSelection) selection;
    wizard.init(workbench, selectionToPass);
    new WizardDialog(window.getShell(), wizard).open();
  }
}
