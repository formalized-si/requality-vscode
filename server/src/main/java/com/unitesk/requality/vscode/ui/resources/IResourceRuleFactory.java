package com.unitesk.requality.vscode.ui.resources;

import com.unitesk.requality.vscode.ui.runtime.ISchedulingRule;

/** {@code org.eclipse.core.resources.IResourceRuleFactory}: the rule of an operation is the resource itself. */
public interface IResourceRuleFactory {
  default ISchedulingRule createRule(IResource resource) {
    return resource;
  }

  default ISchedulingRule modifyRule(IResource resource) {
    return resource;
  }

  default ISchedulingRule deleteRule(IResource resource) {
    return resource;
  }

  default ISchedulingRule refreshRule(IResource resource) {
    return resource;
  }

  default ISchedulingRule copyRule(IResource source, IResource destination) {
    return destination;
  }

  default ISchedulingRule moveRule(IResource source, IResource destination) {
    return destination;
  }

  default ISchedulingRule markerRule(IResource resource) {
    return null;
  }

  default ISchedulingRule buildRule() {
    return null;
  }

  default ISchedulingRule charsetRule(IResource resource) {
    return resource;
  }
}
