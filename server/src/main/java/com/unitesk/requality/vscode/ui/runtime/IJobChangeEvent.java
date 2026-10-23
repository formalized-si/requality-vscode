package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.jobs.IJobChangeEvent}: the job a change happened to, with its delay and result. */
public interface IJobChangeEvent {
  /** The job the event is about. */
  Job getJob();

  /** The delay of a scheduled job in milliseconds (0 otherwise). */
  long getDelay();

  /** The result of a finished job, or {@code null} when the job has not finished. */
  IStatus getResult();
}
