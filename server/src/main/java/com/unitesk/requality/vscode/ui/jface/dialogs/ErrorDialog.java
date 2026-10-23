package com.unitesk.requality.vscode.ui.jface.dialogs;

import com.unitesk.requality.vscode.core.ClientApi.Severity;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.jface.dialogs.ErrorDialog}: the message of a problem with the reason (the message of the status)
 * and the details (the status and its children, those that match the display mask).
 * VSCODE: shown as a VS Code modal message: the message, then "Reason:" and the details list in its detail text.
 */
public final class ErrorDialog {
  private static final int ALL = IStatus.OK | IStatus.INFO | IStatus.WARNING | IStatus.ERROR;

  private ErrorDialog() {
  }

  public static int openError(Shell parent, String title, String message, IStatus status) {
    return openError(parent, title, message, status, ALL);
  }

  /** Opens the dialog unless no status matches the mask (then OK is returned without showing anything). */
  public static int openError(Shell parent, String title, String message, IStatus status, int displayMask) {
    if (status != null && !shouldDisplay(status, displayMask))
      return Window.OK;
    String reason = status == null ? null : status.getMessage();
    String text = message != null ? message : reason;
    StringBuilder detail = new StringBuilder();
    // ErrorDialog(...): JFaceResources.format("Reason", message, status.getMessage()) when a message is given
    if (message != null && reason != null && !reason.isEmpty())
      detail.append("Reason:\n").append(reason);
    // the details list (ErrorDialog.populateList): the status and its children, indented by depth
    if (status != null && status.getChildren().length > 0) {
      StringBuilder list = new StringBuilder();
      populateList(list, status, 0, displayMask);
      if (list.length() > 0) {
        if (detail.length() > 0)
          detail.append("\n\n");
        detail.append(list);
      }
    }
    NativeDialogs.client().showMessage(status != null && status.getSeverity() == IStatus.WARNING ? Severity.warning
        : status != null && status.getSeverity() == IStatus.INFO ? Severity.info : Severity.error,
        title == null ? "Problem Occurred" : title, text, detail.length() == 0 ? null : detail.toString(), true, true,
        IDialogConstants.OK_LABEL);
    return Window.OK;
  }

  /** {@code ErrorDialog.shouldDisplay}: whether the status or one of its children matches the mask. */
  private static boolean shouldDisplay(IStatus status, int mask) {
    IStatus[] children = status.getChildren();
    if (children == null || children.length == 0)
      return status.matches(mask);
    for (IStatus child : children)
      if (child.matches(mask))
        return true;
    return false;
  }

  /** {@code ErrorDialog.populateList}: the lines of the status and its children (nested ones indented). */
  private static void populateList(StringBuilder list, IStatus status, int nesting, int mask) {
    if (!status.matches(mask))
      return;
    String message = status.getMessage();
    if (message != null && !message.isEmpty()) {
      if (list.length() > 0)
        list.append('\n');
      list.append("  ".repeat(nesting)).append(message);
    }
    for (IStatus child : status.getChildren())
      populateList(list, child, nesting + 1, mask);
  }
}
