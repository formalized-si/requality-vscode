package com.unitesk.requality.vscode.port.eclipse.core;

import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.runtime.Job;

public abstract class RepeatingJob extends Job {
  protected boolean running = true;
  protected boolean pause = false;
  protected long repeatDelay = 0;

  public RepeatingJob(String jobName, long repeatPeriod) {
    super(jobName);
    repeatDelay = repeatPeriod;
  }

  protected IStatus run(IProgressMonitor monitor) {
    if (!running)
      return Status.CANCEL_STATUS;
    schedule(repeatDelay);//schedules this task to rerun after specified delay 
    return Status.OK_STATUS;
  }

  @Override
  public boolean shouldSchedule() {
    return running;
  }

  public void stop() {
    running = false;
  }

  public void pause() {
    pause = true;
  }

  public void start() {
    pause = false;
  }
}
