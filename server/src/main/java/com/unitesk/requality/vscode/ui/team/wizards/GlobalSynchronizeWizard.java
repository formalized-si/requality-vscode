package com.unitesk.requality.vscode.ui.team.wizards;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;

/**
 * {@code org.eclipse.team.internal.ui.wizards.GlobalSynchronizeWizard}: the Synchronize wizard of Team &gt; Synchronize...,
 * which selects the synchronize type and then runs the wizard of that type.
 */
public class GlobalSynchronizeWizard extends Wizard {
  private GlobalRefreshWizardSelectionPage mainPage;

  public GlobalSynchronizeWizard() {
    setWindowTitle("Synchronize");
    setForcePreviousAndNextButtons(true);
    setNeedsProgressMonitor(false);
  }

  @Override
  public void addPages() {
    mainPage = new GlobalRefreshWizardSelectionPage();
    addPage(mainPage);
  }

  @Override
  public boolean canFinish() {
    if (getContainer().getCurrentPage() == mainPage)
      return mainPage.getSelectedWizard() != null && mainPage.getNextPage() == null;
    return super.canFinish();
  }

  @Override
  public boolean performFinish() {
    if (getContainer().getCurrentPage() == mainPage) {
      IWizard noPageWizard = mainPage.getSelectedWizard();
      if (noPageWizard != null && noPageWizard.canFinish()) {
        mainPage.savePageSettings();
        return noPageWizard.performFinish();
      }
    }
    mainPage.savePageSettings();
    return true;
  }
}
