package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.runtime.IStatus;

/** {@code org.eclipse.ui.PartInitException}. */
public class PartInitException extends WorkbenchException {
  private static final long serialVersionUID = 1L;

  public PartInitException(String message) {
    super(message);
  }

  public PartInitException(String message, Throwable cause) {
    super(message, cause);
  }

  public PartInitException(IStatus status) {
    super(status);
  }
}
