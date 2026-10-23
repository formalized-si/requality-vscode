package com.unitesk.requality.vscode.ui.ide.datatransfer;

import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.workbench.IImportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.ui.wizards.datatransfer.FileSystemImportWizard} ("File System"): imports resources from a directory
 * of the local file system into an existing project.
 */
public class FileSystemImportWizard extends Wizard implements IImportWizard {
  private IStructuredSelection selection;
  private FileSystemImportPage1 mainPage;
  private IWorkbench workbench;

  public FileSystemImportWizard() {
  }

  @Override
  public void addPages() {
    super.addPages();
    mainPage = new FileSystemImportPage1("fileSystemImportPage1", workbench, selection);
    addPage(mainPage);
  }

  @Override
  public void init(IWorkbench currentWorkbench, IStructuredSelection currentSelection) {
    this.workbench = currentWorkbench;
    this.selection = currentSelection;
    java.util.List<com.unitesk.requality.vscode.ui.resources.IResource> selectedResources = IDE
        .computeSelectedResources(currentSelection);
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
