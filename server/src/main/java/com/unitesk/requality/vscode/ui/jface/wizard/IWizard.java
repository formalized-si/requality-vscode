package com.unitesk.requality.vscode.ui.jface.wizard;

/** {@code org.eclipse.jface.wizard.IWizard}. */
public interface IWizard {
  void addPages();

  boolean canFinish();

  void createPageControls(com.unitesk.requality.vscode.ui.swt.widgets.Composite pageContainer);

  IWizardContainer getContainer();

  IWizardPage getNextPage(IWizardPage page);

  IWizardPage getPreviousPage(IWizardPage page);

  IWizardPage getPage(String pageName);

  int getPageCount();

  IWizardPage[] getPages();

  IWizardPage getStartingPage();

  String getWindowTitle();

  boolean needsPreviousAndNextButtons();

  boolean needsProgressMonitor();

  boolean performCancel();

  boolean performFinish();

  void setContainer(IWizardContainer wizardContainer);

  void dispose();
}
