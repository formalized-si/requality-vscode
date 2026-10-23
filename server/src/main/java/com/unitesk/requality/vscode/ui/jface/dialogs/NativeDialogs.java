package com.unitesk.requality.vscode.ui.jface.dialogs;

import com.unitesk.requality.vscode.core.ClientApi;
import com.unitesk.requality.vscode.core.ClientApi.Severity;
import com.unitesk.requality.vscode.core.ServerContext;

/** Message and input dialogs shown natively by VS Code (modal), used by the JFace dialog helpers. */
final class NativeDialogs {
  private NativeDialogs() {
  }

  static ClientApi client() {
    ClientApi c = ServerContext.client();
    if (c == null)
      throw new IllegalStateException("No VS Code client");
    return c;
  }

  static Severity severity(int kind) {
    switch (kind) {
      case MessageDialog.ERROR:
        return Severity.error;
      case MessageDialog.WARNING:
        return Severity.warning;
      default:
        return Severity.info;
    }
  }

  /** Returns the index of the chosen label, or -1 when dismissed. */
  static int show(int kind, String title, String message, String[] labels) {
    String[] items = labels == null ? new String[0] : labels;
    String r = client().showMessage(severity(kind), title, message, null, true, true, items);
    if (r == null)
      return -1;
    for (int i = 0; i < items.length; i++)
      if (items[i].equals(r))
        return i;
    return -1;
  }
}
