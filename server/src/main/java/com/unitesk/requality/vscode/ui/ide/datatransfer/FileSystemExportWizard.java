package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.util.List;

import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.workbench.IExportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.ui.wizards.datatransfer.FileSystemExportWizard} ("File System"): exports resources of the workspace to a
 * directory of the local file system.
 */
public class FileSystemExportWizard extends Wizard implements IExportWizard {
  private List<IResource> selection = new java.util.ArrayList<>();
  private WizardFileSystemResourceExportPage1 mainPage;

  public FileSystemExportWizard() {
  }

  @Override
  public void addPages() {
    super.addPages();
    mainPage = new WizardFileSystemResourceExportPage1("fileSystemExportPage1", selection);
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
