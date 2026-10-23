package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;

/** {@code org.eclipse.ui.dialogs.IWorkingSetNewWizard}: the wizard that creates a working set. */
public interface IWorkingSetNewWizard extends IWizard {
  /** The created working set, or null when the wizard was cancelled. */
  IWorkingSet getSelection();
}
