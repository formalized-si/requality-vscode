package com.unitesk.requality.vscode.ui.ide.wizards;

import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WizardNewFileCreationPage;

/**
 * {@code org.eclipse.ui.wizards.newresource.BasicNewFileResourceWizard}: the new file wizard (General > File). Finish
 * creates the file, selects it in the explorer and opens it in the default editor.
 */
public class BasicNewFileResourceWizard extends BasicNewResourceWizard {
  /** The wizard id for creating new files in the workspace. */
  public static final String WIZARD_ID = "org.eclipse.ui.wizards.new.file";

  private WizardNewFileCreationPage mainPage;

  /** Creates a wizard for creating a new file resource in the workspace. */
  public BasicNewFileResourceWizard() {
    super();
  }

  @Override
  public void addPages() {
    super.addPages();
    mainPage = new WizardNewFileCreationPage("newFilePage1", getSelection()) {
      @Override
      protected java.io.InputStream getInitialContents() {
        return new java.io.ByteArrayInputStream(new byte[0]);
      }
    };
    mainPage.setTitle("File");
    mainPage.setDescription("Create a new file resource.");
    addPage(mainPage);
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection currentSelection) {
    super.init(workbench, currentSelection);
    setWindowTitle("Create New File");
    setNeedsProgressMonitor(true);
  }

  @Override
  public boolean performFinish() {
    IFile file = mainPage.createNewFile();
    if (file == null)
      return false;
    selectAndReveal(file);
    IDE.openFile(file);
    return true;
  }
}
