package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.IStatus}. */
public interface IStatus {
  int OK = 0;
  int INFO = 0x01;
  int WARNING = 0x02;
  int ERROR = 0x04;
  int CANCEL = 0x08;

  int getSeverity();

  String getMessage();

  String getPlugin();

  int getCode();

  Throwable getException();

  default boolean isMultiStatus() {
    return false;
  }

  default boolean isOK() {
    return getSeverity() == OK;
  }

  default IStatus[] getChildren() {
    return new IStatus[0];
  }

  default boolean matches(int severityMask) {
    return (getSeverity() & severityMask) != 0;
  }
}
