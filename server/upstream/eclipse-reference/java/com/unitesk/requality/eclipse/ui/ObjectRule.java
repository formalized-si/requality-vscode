package com.unitesk.requality.eclipse.ui;

import org.eclipse.core.runtime.jobs.ISchedulingRule;

public class ObjectRule implements ISchedulingRule {

  Object o;
  
  public ObjectRule(Object o){
    this.o = o;
  }
  
  @Override
  public boolean contains(ISchedulingRule rule) {
    if(!(rule instanceof ObjectRule))
      return false;
    return ((ObjectRule)rule).o==o;
  }

  @Override
  public boolean isConflicting(ISchedulingRule rule) {
    return contains(rule);
  }
}
