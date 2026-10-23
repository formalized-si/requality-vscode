package com.unitesk.requality.vscode.ui.navigator.wizards;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWizardDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWizard;

/**
 * {@code org.eclipse.ui.internal.navigator.wizards.WizardShortcutAction}: an action that opens a wizard of the wizard
 * registry, initialized with the selection of the window, in a wizard dialog.
 */
public class WizardShortcutAction extends Action {
  private final IWorkbenchWindow window;
  private final IWizardDescriptor descriptor;

  public WizardShortcutAction(IWorkbenchWindow window, IWizardDescriptor descriptor) {
    super(descriptor.getLabel());
    this.window = window;
    this.descriptor = descriptor;
    setToolTipText(descriptor.getDescription());
  }

  public IWizardDescriptor getWizardDescriptor() {
    return descriptor;
  }

  @Override
  public void run() {
    IWorkbenchWizard wizard;
    try {
      wizard = (IWorkbenchWizard) descriptor.createWizard();
    } catch (CoreException e) {
      ErrorDialog.openError(window.getShell(), "Problems Creating Wizard", descriptor.getLabel(), e.getStatus());
      return;
    }
    ISelection selection = window.getSelectionService().getSelection();
    if (selection instanceof IStructuredSelection)
      wizard.init(window.getWorkbench(), (IStructuredSelection) selection);
    else
      wizard.init(window.getWorkbench(), StructuredSelection.EMPTY);
    Shell parent = window.getShell();
    new WizardDialog(parent, wizard).open();
  }
}
