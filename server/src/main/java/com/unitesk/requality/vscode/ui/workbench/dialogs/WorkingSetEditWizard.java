package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;

/** {@code org.eclipse.ui.internal.dialogs.WorkingSetEditWizard}: edits a working set with the page of its type. */
public class WorkingSetEditWizard extends Wizard implements IWorkingSetEditWizard {
  private final IWorkingSetPage workingSetEditPage;
  private IWorkingSet workingSet;

  /**
   * Creates a new instance of the receiver.
   *
   * @param editPage the working set page used for editing a working set
   */
  public WorkingSetEditWizard(IWorkingSetPage editPage) {
    super();
    workingSetEditPage = editPage;
    workingSetEditPage.setWizard(this);
    setWindowTitle("Edit Working Set");
  }

  @Override
  public void addPages() {
    super.addPages();
    addPage(workingSetEditPage);
  }

  @Override
  public boolean canFinish() {
    return workingSetEditPage.isPageComplete();
  }

  @Override
  public IWorkingSet getSelection() {
    return workingSet;
  }

  @Override
  public boolean performFinish() {
    workingSetEditPage.finish();
    return true;
  }

  @Override
  public void setSelection(IWorkingSet workingSet) {
    this.workingSet = workingSet;
    workingSetEditPage.setSelection(workingSet);
  }
}
