package com.unitesk.requality.vscode.ui.jface.wizard;

import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/** {@code org.eclipse.jface.wizard.IWizardPage}. */
public interface IWizardPage {
  boolean canFlipToNextPage();

  void createControl(Composite parent);

  Control getControl();

  String getName();

  String getTitle();

  String getDescription();

  String getErrorMessage();

  String getMessage();

  int getMessageType();

  IWizardPage getNextPage();

  IWizardPage getPreviousPage();

  IWizard getWizard();

  boolean isPageComplete();

  void setPreviousPage(IWizardPage page);

  void setWizard(IWizard newWizard);

  void setVisible(boolean visible);

  void dispose();
}
