package com.unitesk.requality.vscode.ui.ide.datatransfer;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.workbench.IImportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.ui.wizards.datatransfer.ExternalProjectImportWizard} ("Existing Projects into Workspace"): adds the
 * Eclipse projects of a root directory or an archive file to the workspace.
 */
public class ExternalProjectImportWizard extends Wizard implements IImportWizard {
  private WizardProjectsImportPage mainPage;

  public ExternalProjectImportWizard() {
  }

  @Override
  public void addPages() {
    super.addPages();
    mainPage = new WizardProjectsImportPage("wizardExternalProjectsPage");
    addPage(mainPage);
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    setWindowTitle("Import");
    setNeedsProgressMonitor(true);
  }

  @Override
  public boolean performFinish() {
    return mainPage.finish();
  }
}
