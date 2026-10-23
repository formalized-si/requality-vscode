package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.jobs.JobChangeAdapter}: a job change listener that ignores every event. */
public abstract class JobChangeAdapter implements IJobChangeListener {
  @Override
  public void aboutToRun(IJobChangeEvent event) {
  }

  @Override
  public void awake(IJobChangeEvent event) {
  }

  @Override
  public void done(IJobChangeEvent event) {
  }

  @Override
  public void running(IJobChangeEvent event) {
  }

  @Override
  public void scheduled(IJobChangeEvent event) {
  }

  @Override
  public void sleeping(IJobChangeEvent event) {
  }
}
