package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.workbench.IWizardCategory;
import com.unitesk.requality.vscode.ui.workbench.IWizardDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWizard;

/**
 * {@code org.eclipse.ui.internal.dialogs.NewWizardSelectionPage}: the page that allows the user to select a registered
 * new wizard to run. Selecting a wizard creates it, initializes it with the selection and adds its pages after this
 * page (Next); a wizard without pages can be finished from this page.
 */
public class NewWizardSelectionPage extends WizardPage {
  private final IWizardCategory wizardCategories;
  private final IStructuredSelection selection;
  private final IWorkbench workbench;
  private final boolean projectsOnly;
  private NewWizardNewPage newResourcePage;
  private IWizardDescriptor selectedDescriptor;
  private IWizard selectedWizard;
  private boolean canFinishEarly = false;
  private boolean hasPages = false;

  /**
   * @param workbench the workbench
   * @param selection the current selection
   * @param root the wizard root element
   * @param projectsOnly true if only the project wizards are shown
   */
  public NewWizardSelectionPage(IWorkbench workbench, IStructuredSelection selection, IWizardCategory root,
      boolean projectsOnly) {
    super("newWizardSelectionPage");
    setTitle("Select a wizard");
    setDescription("The following resource creation wizards are available.");
    this.workbench = workbench;
    this.selection = selection;
    this.wizardCategories = root;
    this.projectsOnly = projectsOnly;
    setPageComplete(false);
  }

  @Override
  public void createControl(Composite parent) {
    newResourcePage = new NewWizardNewPage(this, wizardCategories, projectsOnly);
    Control control = newResourcePage.createControl(parent);
    setControl(control);
  }

  /** Makes the next page visible, or finishes the selected wizard if it has no pages. */
  public void advanceToNextPageOrFinish() {
    if (canFlipToNextPage()) {
      getContainer().showPage(getNextPage());
    } else if (canFinishEarly()) {
      if (getWizard().performFinish() && getContainer() instanceof WizardDialog)
        ((WizardDialog) getContainer()).close();
    }
  }

  /** The pages of the selected wizard follow this page. */
  @Override
  public IWizardPage getNextPage() {
    if (selectedWizard == null || !hasPages)
      return null;
    return selectedWizard.getStartingPage();
  }

  @Override
  public boolean canFlipToNextPage() {
    return getNextPage() != null;
  }

  /** The wizard selected in the tree, or null. */
  public IWizard getSelectedWizard() {
    return selectedWizard;
  }

  /** Selects the wizard (or no wizard, for null): creates it, adds its pages and sets whether it can finish early. */
  void updateWizardSelection(IWizardDescriptor descriptor) {
    if (descriptor == selectedDescriptor && descriptor != null)
      return;
    selectedDescriptor = descriptor;
    selectedWizard = null;
    hasPages = false;
    canFinishEarly = false;
    setErrorMessage(null);
    if (descriptor == null) {
      setPageComplete(false);
      return;
    }
    try {
      IWizard wizard = descriptor.createWizard();
      if (wizard instanceof IWorkbenchWizard)
        ((IWorkbenchWizard) wizard).init(workbench, selection);
      wizard.setContainer(getContainer());
      wizard.addPages();
      selectedWizard = wizard;
      hasPages = wizard.getPageCount() > 0;
      canFinishEarly = !hasPages;
      setPageComplete(true);
    } catch (CoreException e) {
      setErrorMessage(e.getMessage());
      setPageComplete(false);
    }
  }

  /** Whether the selected wizard can finish from this page (it has no pages). */
  public boolean canFinishEarly() {
    return canFinishEarly;
  }
}
