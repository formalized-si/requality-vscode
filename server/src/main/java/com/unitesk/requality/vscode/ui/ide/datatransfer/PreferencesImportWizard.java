package com.unitesk.requality.vscode.ui.ide.datatransfer;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.workbench.IImportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.ui.internal.preferences.PreferencesImportWizard} ("Preferences"): imports the preferences of the plug-in
 * from a preference file (.epf).
 */
public class PreferencesImportWizard extends Wizard implements IImportWizard {
  private PreferencesImportPage mainPage;

  public PreferencesImportWizard() {
  }

  @Override
  public void addPages() {
    super.addPages();
    mainPage = new PreferencesImportPage("preferencesImportPage");
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
