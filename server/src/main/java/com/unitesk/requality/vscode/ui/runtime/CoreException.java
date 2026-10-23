package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.CoreException}. */
public class CoreException extends Exception {
  private static final long serialVersionUID = 1L;
  private final IStatus status;

  public CoreException(IStatus status) {
    super(status.getMessage(), status.getException());
    this.status = status;
  }

  public IStatus getStatus() {
    return status;
  }
}
