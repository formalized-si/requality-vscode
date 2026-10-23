package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.NullProgressMonitor}. */
public class NullProgressMonitor implements IProgressMonitor {
  private volatile boolean canceled;

  @Override
  public void beginTask(String name, int totalWork) {
  }

  @Override
  public void done() {
  }

  @Override
  public boolean isCanceled() {
    return canceled;
  }

  @Override
  public void setCanceled(boolean value) {
    canceled = value;
  }

  @Override
  public void worked(int work) {
  }
}
