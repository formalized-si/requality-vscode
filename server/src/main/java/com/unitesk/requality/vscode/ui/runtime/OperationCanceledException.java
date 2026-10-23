package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.OperationCanceledException}. */
public class OperationCanceledException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public OperationCanceledException() {
  }

  public OperationCanceledException(String message) {
    super(message);
  }
}
