package com.unitesk.requality.vscode.ui.workbench.actions;

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
 * {@code org.eclipse.ui.actions.NewWizardAction}: opens the new wizard selection dialog ({@code New} command of
 * {@code ActionFactory.NEW}), with the selection of the window; the menu text is "Other...".
 */
public class NewWizardAction extends Action {
  private final IWorkbenchWindow window;
  private String categoryId;
  private String wizardWindowTitle;

  public NewWizardAction(IWorkbenchWindow window) {
    super("&Other...");
    if (window == null)
      throw new IllegalArgumentException();
    this.window = window;
  }

  /** The id of the category shown first, or null for all categories. */
  public String getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(String id) {
    categoryId = id;
  }

  public void setWizardWindowTitle(String windowTitle) {
    wizardWindowTitle = windowTitle;
  }

  @Override
  public void run() {
    IWorkbench workbench = PlatformUI.getWorkbench();
    NewWizard wizard = new NewWizard();
    if (categoryId != null)
      wizard.setCategoryId(categoryId);
    ISelection selection = window.getSelectionService().getSelection();
    IStructuredSelection selectionToPass = StructuredSelection.EMPTY;
    if (selection instanceof IStructuredSelection)
      selectionToPass = (IStructuredSelection) selection;
    wizard.init(workbench, selectionToPass);
    if (wizardWindowTitle != null)
      wizard.setWindowTitle(wizardWindowTitle);
    new WizardDialog(window.getShell(), wizard).open();
  }
}
