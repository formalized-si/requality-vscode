package com.unitesk.requality.vscode.ui.swt;

/** {@code org.eclipse.swt.SWTError}: an error reported by a browser or another SWT widget. */
public class SWTError extends Error {
  private static final long serialVersionUID = 1L;

  public SWTError(int code) {
    super("SWT error " + code);
  }

  public SWTError(String message) {
    super(message);
  }
}
