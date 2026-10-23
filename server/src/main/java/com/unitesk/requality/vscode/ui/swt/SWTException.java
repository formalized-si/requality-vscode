package com.unitesk.requality.vscode.ui.swt;

/** {@code org.eclipse.swt.SWTException}. */
public class SWTException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  /** The SWT error code. */
  public int code;
  public Throwable throwable;

  public SWTException() {
    this(SWT.ERROR_UNSPECIFIED);
  }

  public SWTException(int code) {
    this(code, "Unspecified error");
  }

  public SWTException(String message) {
    this(SWT.ERROR_UNSPECIFIED, message);
  }

  public SWTException(int code, String message) {
    super(message);
    this.code = code;
  }

  @Override
  public Throwable getCause() {
    return throwable;
  }
}
