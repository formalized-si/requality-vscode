package com.unitesk.requality.core.api;

import com.unitesk.requality.core.attribute.reference.IReferenceVersionRule;
import com.unitesk.requality.core.model.TreeDB;

public interface IReferenceVersionRuleProvider {
  public IReferenceVersionRule getRule(TreeDB db);
}
