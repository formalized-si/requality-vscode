package com.unitesk.requality.vscode.port.marker;

import com.unitesk.requality.nodetype.Requirement;

public class RequirementDecorator extends TreeNodeStatusDecorator {

  @Override
  protected String getStatusPrefix() {
    return "rq_";
  }

  @Override
  protected String getAttributeName() {
    return Requirement.ATTR_STATUS;
  }
}
