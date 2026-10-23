package com.unitesk.requality.vscode.ui.jface.dialogs;

import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.jface.dialogs.MessageDialogWithToggle}: the toggle is an extra "Do not show again" button. */
public class MessageDialogWithToggle {
  private final int returnCode;
  private final boolean toggleState;

  private MessageDialogWithToggle(int returnCode, boolean toggleState) {
    this.returnCode = returnCode;
    this.toggleState = toggleState;
  }

  public int getReturnCode() {
    return returnCode;
  }

  public boolean getToggleState() {
    return toggleState;
  }

  private static MessageDialogWithToggle open(int kind, String title, String message, String toggle, boolean state,
      IPreferenceStore store, String key, String[] labels) {
    String[] items = new String[labels.length + 1];
    System.arraycopy(labels, 0, items, 0, labels.length);
    items[labels.length] = toggle == null ? "Do not show again" : toggle;
    int r = NativeDialogs.show(kind, title, message, items);
    boolean toggled = r == labels.length || state;
    if (r == labels.length)
      r = 0;
    if (store != null && key != null && toggled)
      store.setValue(key, true);
    return new MessageDialogWithToggle(r < 0 ? IDialogConstants.CANCEL_ID : r, toggled);
  }

  public static MessageDialogWithToggle openInformation(Shell parent, String title, String message, String toggle,
      boolean state, IPreferenceStore store, String key) {
    return open(MessageDialog.INFORMATION, title, message, toggle, state, store, key,
        new String[] { IDialogConstants.OK_LABEL });
  }

  public static MessageDialogWithToggle openYesNoQuestion(Shell parent, String title, String message, String toggle,
      boolean state, IPreferenceStore store, String key) {
    MessageDialogWithToggle d = open(MessageDialog.QUESTION, title, message, toggle, state, store, key,
        new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL });
    return new MessageDialogWithToggle(d.returnCode == 0 ? IDialogConstants.YES_ID : IDialogConstants.NO_ID,
        d.toggleState);
  }

  public static MessageDialogWithToggle openOkCancelConfirm(Shell parent, String title, String message,
      String toggle, boolean state, IPreferenceStore store, String key) {
    MessageDialogWithToggle d = open(MessageDialog.CONFIRM, title, message, toggle, state, store, key,
        new String[] { IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL });
    return new MessageDialogWithToggle(d.returnCode == 0 ? IDialogConstants.OK_ID : IDialogConstants.CANCEL_ID,
        d.toggleState);
  }
}
