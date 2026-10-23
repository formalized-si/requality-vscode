package com.unitesk.requality.vscode.ui.ide.datatransfer;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.workbench.IExportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.ui.internal.preferences.PreferencesExportWizard} ("Preferences"): exports the preferences of the plug-in
 * to a preference file (.epf).
 */
public class PreferencesExportWizard extends Wizard implements IExportWizard {
  private PreferencesExportPage mainPage;

  public PreferencesExportWizard() {
  }

  @Override
  public void addPages() {
    super.addPages();
    mainPage = new PreferencesExportPage("preferencesExportPage");
    addPage(mainPage);
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    setWindowTitle("Export");
    setNeedsProgressMonitor(true);
  }

  @Override
  public boolean performFinish() {
    return mainPage.finish();
  }
}
