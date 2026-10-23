package com.unitesk.requality.core.model;

import com.unitesk.requality.core.operation.ChangesSet;

public interface IVersioningRule<T> {
  public boolean match(String type, ChangesSet<T> cs);
}
