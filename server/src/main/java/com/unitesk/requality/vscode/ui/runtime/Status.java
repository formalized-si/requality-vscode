package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.Status}. */
public class Status implements IStatus {
  public static final IStatus OK_STATUS = new Status(OK, "com.unitesk.requality", 0, "OK", null);
  public static final IStatus CANCEL_STATUS = new Status(CANCEL, "com.unitesk.requality", 1, "", null);

  private final int severity;
  private final String plugin;
  private final int code;
  private final String message;
  private final Throwable exception;

  public Status(int severity, String pluginId, int code, String message, Throwable exception) {
    this.severity = severity;
    this.plugin = pluginId;
    this.code = code;
    this.message = message == null ? "" : message;
    this.exception = exception;
  }

  public Status(int severity, String pluginId, String message) {
    this(severity, pluginId, 0, message, null);
  }

  public Status(int severity, String pluginId, String message, Throwable exception) {
    this(severity, pluginId, 0, message, exception);
  }

  @Override
  public int getSeverity() {
    return severity;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public String getPlugin() {
    return plugin;
  }

  @Override
  public int getCode() {
    return code;
  }

  @Override
  public Throwable getException() {
    return exception;
  }

  @Override
  public String toString() {
    return "Status " + severity + ": " + message;
  }
}
