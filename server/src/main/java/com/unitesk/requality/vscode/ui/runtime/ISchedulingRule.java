package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.jobs.ISchedulingRule}: rules are not enforced remotely. */
public interface ISchedulingRule {
  boolean contains(ISchedulingRule rule);

  boolean isConflicting(ISchedulingRule rule);
}
