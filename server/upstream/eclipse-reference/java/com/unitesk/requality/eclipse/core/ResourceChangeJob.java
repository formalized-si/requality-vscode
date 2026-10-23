package com.unitesk.requality.eclipse.core;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

public class ResourceChangeJob {
  public IWorkspaceRunnable job;
  public ISchedulingRule rule;
  public ResourceChangeJob(IWorkspaceRunnable job, ISchedulingRule rule) {
    this.job = job;
    this.rule = rule;
  }
}
