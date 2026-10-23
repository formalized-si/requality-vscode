package com.unitesk.requality.vscode.ui.ide.wizards;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WizardNewFolderMainPage;

/**
 * {@code org.eclipse.ui.wizards.newresource.BasicNewFolderResourceWizard}: the new folder wizard (General > Folder).
 * Finish creates the folder and selects it in the explorer.
 */
public class BasicNewFolderResourceWizard extends BasicNewResourceWizard {
  /** The wizard id for creating new folders in the workspace. */
  public static final String WIZARD_ID = "org.eclipse.ui.wizards.new.folder";

  private WizardNewFolderMainPage mainPage;

  /** Creates a wizard for creating a new folder resource in the workspace. */
  public BasicNewFolderResourceWizard() {
    super();
  }

  @Override
  public void addPages() {
    super.addPages();
    mainPage = new WizardNewFolderMainPage("Folder", getSelection());
    addPage(mainPage);
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection currentSelection) {
    super.init(workbench, currentSelection);
    setWindowTitle("New Folder");
    setNeedsProgressMonitor(true);
  }

  @Override
  public boolean performFinish() {
    IFolder folder = mainPage.createNewFolder();
    if (folder == null)
      return false;
    selectAndReveal(folder);
    return true;
  }
}
