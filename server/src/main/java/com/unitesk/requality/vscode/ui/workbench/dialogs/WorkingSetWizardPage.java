package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;

/**
 * {@code org.eclipse.ui.dialogs.WorkingSetWizardPage}: the base of the working set pages (the page-complete state of the
 * wizard page). VSCODE: a plain base over the compat WizardPage, which keeps the page-complete state itself.
 */
public abstract class WorkingSetWizardPage extends WizardPage {
  protected WorkingSetWizardPage(String pageName, String title, Object titleImage) {
    super(pageName, title, titleImage);
  }
}
