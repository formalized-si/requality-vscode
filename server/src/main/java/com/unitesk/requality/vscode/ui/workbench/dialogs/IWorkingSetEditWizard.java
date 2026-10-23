package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;

/** {@code org.eclipse.ui.dialogs.IWorkingSetEditWizard}: the wizard that edits a working set. */
public interface IWorkingSetEditWizard extends IWizard {
  /** The edited working set. */
  IWorkingSet getSelection();

  /** Sets the working set to edit. */
  void setSelection(IWorkingSet workingSet);
}
