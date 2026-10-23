package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetDescriptor;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetRegistry;

/**
 * {@code org.eclipse.ui.internal.dialogs.WorkingSetNewWizard}: creates a working set with the page of its type. Several
 * types give a first page to choose the type.
 */
public class WorkingSetNewWizard extends Wizard implements IWorkingSetNewWizard {
  private WorkingSetTypePage workingSetTypePage;
  private IWorkingSetPage workingSetEditPage;
  private String editPageId;
  private IWorkingSet workingSet;
  private final WorkingSetDescriptor[] descriptors;

  /**
   * Creates a new instance of the receiver.
   *
   * @param descriptors the choice of working set types
   */
  public WorkingSetNewWizard(WorkingSetDescriptor[] descriptors) {
    super();
    Assert.isTrue(descriptors != null && descriptors.length > 0);
    this.descriptors = descriptors;
    setWindowTitle("New Working Set");
  }

  @Override
  public void addPages() {
    super.addPages();
    IWizardPage page;
    if (descriptors.length > 1) {
      page = workingSetTypePage = new WorkingSetTypePage(this.descriptors);
    } else {
      editPageId = descriptors[0].getId();
      page = workingSetEditPage = WorkingSetRegistry.get().getWorkingSetPage(editPageId);
    }
    page.setWizard(this);
    addPage(page);
    setForcePreviousAndNextButtons(descriptors.length > 1);
  }

  @Override
  public boolean canFinish() {
    return workingSetEditPage != null && workingSetEditPage.isPageComplete();
  }

  @Override
  public IWizardPage getNextPage(IWizardPage page) {
    if (workingSetTypePage != null && page == workingSetTypePage) {
      String pageId = workingSetTypePage.getSelection();
      if (pageId != null) {
        if (workingSetEditPage == null || !pageId.equals(editPageId)) {
          workingSetEditPage = WorkingSetRegistry.get().getWorkingSetPage(pageId);
          addPage(workingSetEditPage);
          editPageId = pageId;
        }
        return workingSetEditPage;
      }
    }
    return null;
  }

  @Override
  public IWorkingSet getSelection() {
    return workingSet;
  }

  @Override
  public boolean performFinish() {
    workingSetEditPage.finish();
    workingSet = workingSetEditPage.getSelection();
    workingSet.setId(editPageId);
    return true;
  }
}
