package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.util.List;

import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.workbench.IExportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.ui.wizards.datatransfer.ZipFileExportWizard} ("Archive File"): exports resources of the workspace into a
 * zip archive file on the local file system.
 */
public class ZipFileExportWizard extends Wizard implements IExportWizard {
  private List<IResource> selection = new java.util.ArrayList<>();
  private WizardArchiveFileResourceExportPage1 mainPage;

  public ZipFileExportWizard() {
  }

  @Override
  public void addPages() {
    super.addPages();
    mainPage = new WizardArchiveFileResourceExportPage1("zipFileExportPage1", selection);
    addPage(mainPage);
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection currentSelection) {
    selection = IDE.computeSelectedResources(currentSelection);
    setWindowTitle("Export");
    setNeedsProgressMonitor(true);
  }

  @Override
  public boolean performFinish() {
    return mainPage.finish();
  }
}
