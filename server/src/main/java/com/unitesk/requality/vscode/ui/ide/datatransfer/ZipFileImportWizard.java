package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.util.List;

import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.workbench.IImportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.ui.wizards.datatransfer.ZipFileImportWizard} ("Archive File"): imports resources from a zip archive
 * into an existing project.
 */
public class ZipFileImportWizard extends Wizard implements IImportWizard {
  private IStructuredSelection selection;
  private IWorkbench workbench;
  private WizardArchiveFileResourceImportPage1 mainPage;

  public ZipFileImportWizard() {
  }

  @Override
  public void addPages() {
    super.addPages();
    mainPage = new WizardArchiveFileResourceImportPage1("zipFileImportPage1", workbench, selection);
    addPage(mainPage);
  }

  @Override
  public void init(IWorkbench currentWorkbench, IStructuredSelection currentSelection) {
    this.workbench = currentWorkbench;
    this.selection = currentSelection;
    List<IResource> selectedResources = IDE.computeSelectedResources(currentSelection);
    if (!selectedResources.isEmpty())
      this.selection = new StructuredSelection(selectedResources);
    setWindowTitle("Import");
    setNeedsProgressMonitor(true);
  }

  @Override
  public boolean performFinish() {
    return mainPage.finish();
  }
}
