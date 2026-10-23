package com.unitesk.requality.vscode.ui.runtime;

/**
 * {@code org.eclipse.core.runtime.IProgressMonitorWithBlocking}: a monitor that can tell the user that its job is
 * blocked (waiting for a lock or a user operation). The Progress view shows the reason as
 * {@code (Blocked: reason)} until {@link #clearBlocked()}.
 */
public interface IProgressMonitorWithBlocking extends IProgressMonitor {
  /** Marks the job as blocked; the status message is the reason shown to the user. */
  void setBlocked(IStatus reason);

  /** The job is no longer blocked. */
  void clearBlocked();
}
