package com.unitesk.requality.vscode.ui.jface.preference;

import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogPage;

/** {@code org.eclipse.jface.preference.IPreferencePage}. */
public interface IPreferencePage extends IDialogPage {
  boolean isValid();

  boolean okToLeave();

  boolean performOk();

  boolean performCancel();

  void setContainer(PreferenceDialog container);
}
