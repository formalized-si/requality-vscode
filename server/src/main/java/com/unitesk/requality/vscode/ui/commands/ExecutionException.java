package com.unitesk.requality.vscode.ui.commands;

/** {@code org.eclipse.core.commands.ExecutionException}. */
public class ExecutionException extends Exception {
  private static final long serialVersionUID = 1L;

  public ExecutionException(String message) {
    super(message);
  }

  public ExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
