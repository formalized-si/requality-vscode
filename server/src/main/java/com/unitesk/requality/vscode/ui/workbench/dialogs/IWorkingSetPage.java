package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;

/** {@code org.eclipse.ui.dialogs.IWorkingSetPage}: the page of a working set type that creates or edits its sets. */
public interface IWorkingSetPage extends IWizardPage {
  /** Called when the wizard finishes: applies the page's content to the working set. */
  void finish();

  /** The working set the page creates or edits (null before finish when created). */
  IWorkingSet getSelection();

  /** Sets the working set the page edits. */
  void setSelection(IWorkingSet workingSet);
}
