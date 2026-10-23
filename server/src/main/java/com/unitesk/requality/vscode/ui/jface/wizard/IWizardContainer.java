package com.unitesk.requality.vscode.ui.jface.wizard;

import com.unitesk.requality.vscode.ui.jface.operation.IRunnableContext;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.jface.wizard.IWizardContainer}. */
public interface IWizardContainer extends IRunnableContext {
  IWizardPage getCurrentPage();

  Shell getShell();

  void showPage(IWizardPage page);

  void updateButtons();

  void updateMessage();

  void updateTitleBar();

  void updateWindowTitle();
}
