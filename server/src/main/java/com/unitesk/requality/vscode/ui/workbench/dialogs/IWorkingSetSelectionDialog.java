package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;

/** {@code org.eclipse.ui.dialogs.IWorkingSetSelectionDialog}: the dialog that selects working sets. */
public interface IWorkingSetSelectionDialog {
  /** Opens the dialog; returns {@code Window.OK} or {@code Window.CANCEL}. */
  int open();

  /** The selected working sets. */
  IWorkingSet[] getSelection();

  /** Sets the working sets selected at the start. */
  void setSelection(IWorkingSet[] workingSets);
}
