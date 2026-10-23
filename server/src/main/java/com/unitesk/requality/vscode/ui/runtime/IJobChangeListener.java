package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.jobs.IJobChangeListener}: the life cycle of a job. */
public interface IJobChangeListener {
  void aboutToRun(IJobChangeEvent event);

  void awake(IJobChangeEvent event);

  void done(IJobChangeEvent event);

  void running(IJobChangeEvent event);

  void scheduled(IJobChangeEvent event);

  void sleeping(IJobChangeEvent event);
}
