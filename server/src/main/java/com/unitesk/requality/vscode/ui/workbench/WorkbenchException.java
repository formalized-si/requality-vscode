package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

/** {@code org.eclipse.ui.WorkbenchException}. */
public class WorkbenchException extends CoreException {
  private static final long serialVersionUID = 1L;

  public WorkbenchException(String message) {
    super(new Status(IStatus.ERROR, "org.eclipse.ui", message));
  }

  public WorkbenchException(String message, Throwable cause) {
    super(new Status(IStatus.ERROR, "org.eclipse.ui", message, cause));
  }

  public WorkbenchException(IStatus status) {
    super(status);
  }
}
