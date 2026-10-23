package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.internal.jobs.JobChangeEvent}: the event sent to the job change listeners. */
public final class JobChangeEvent implements IJobChangeEvent {
  private final Job job;
  private final long delay;
  private final IStatus result;

  public JobChangeEvent(Job job, long delay, IStatus result) {
    this.job = job;
    this.delay = delay;
    this.result = result;
  }

  @Override
  public Job getJob() {
    return job;
  }

  @Override
  public long getDelay() {
    return delay;
  }

  @Override
  public IStatus getResult() {
    return result;
  }
}
