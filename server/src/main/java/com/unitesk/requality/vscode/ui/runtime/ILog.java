package com.unitesk.requality.vscode.ui.runtime;

/**
 * {@code org.eclipse.core.runtime.ILog}: the log of a plug-in. VSCODE: the entries go to the Requality log (the server
 * log shown by Requality: Show Server Log, Eclipse's Error Log).
 */
public interface ILog {
  void log(IStatus status);

  /** {@code ILog.error(String, Throwable)}. */
  default void error(String message, Throwable exception) {
    log(new Status(IStatus.ERROR, "", message, exception));
  }

  /** {@code ILog.warn(String)}. */
  default void warn(String message) {
    log(new Status(IStatus.WARNING, "", message));
  }

  /** {@code ILog.info(String)}. */
  default void info(String message) {
    log(new Status(IStatus.INFO, "", message));
  }

  /** The log of a plug-in: the statuses are logged by the Requality logger with their severity. */
  static ILog of(String pluginId) {
    return status -> {
      if (status == null)
        return;
      String text = (pluginId == null || pluginId.isEmpty() ? "" : pluginId + ": ") + status.getMessage();
      Throwable e = status.getException();
      if (status.getSeverity() == IStatus.ERROR) {
        if (e instanceof Exception)
          com.unitesk.requality.core.log.RLogger.logError((Exception) e);
        com.unitesk.requality.core.log.RLogger.logError(text);
      } else {
        com.unitesk.requality.core.log.RLogger.logInfo(text);
      }
    };
  }
}
