package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.IProgressMonitor}. */
public interface IProgressMonitor {
  int UNKNOWN = -1;

  void beginTask(String name, int totalWork);

  void done();

  default void internalWorked(double work) {
  }

  boolean isCanceled();

  void setCanceled(boolean value);

  default void setTaskName(String name) {
  }

  default void subTask(String name) {
  }

  void worked(int work);
}
