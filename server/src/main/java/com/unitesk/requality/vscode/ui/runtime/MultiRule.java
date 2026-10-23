package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.jobs.MultiRule}: rules are not enforced remotely, combining keeps the first. */
public final class MultiRule implements ISchedulingRule {
  private final ISchedulingRule[] rules;

  public MultiRule(ISchedulingRule[] rules) {
    this.rules = rules;
  }

  public static ISchedulingRule combine(ISchedulingRule a, ISchedulingRule b) {
    if (a == null)
      return b;
    if (b == null)
      return a;
    return new MultiRule(new ISchedulingRule[] { a, b });
  }

  public static ISchedulingRule combine(ISchedulingRule[] rules) {
    ISchedulingRule r = null;
    for (ISchedulingRule x : rules)
      r = combine(r, x);
    return r;
  }

  public ISchedulingRule[] getChildren() {
    return rules.clone();
  }

  @Override
  public boolean contains(ISchedulingRule rule) {
    for (ISchedulingRule r : rules)
      if (r.contains(rule))
        return true;
    return rule == this;
  }

  @Override
  public boolean isConflicting(ISchedulingRule rule) {
    for (ISchedulingRule r : rules)
      if (r.isConflicting(rule))
        return true;
    return false;
  }
}
