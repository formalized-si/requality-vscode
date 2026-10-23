package com.unitesk.requality.vscode.port.eclipse.core;

import com.unitesk.requality.vscode.ui.resources.IWorkspaceRunnable;
import com.unitesk.requality.vscode.ui.runtime.ISchedulingRule;

public class ResourceChangeJob {
  public IWorkspaceRunnable job;
  public ISchedulingRule rule;
  public ResourceChangeJob(IWorkspaceRunnable job, ISchedulingRule rule) {
    this.job = job;
    this.rule = rule;
  }
}
