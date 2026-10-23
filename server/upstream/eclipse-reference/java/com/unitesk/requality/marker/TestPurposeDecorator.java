package com.unitesk.requality.marker;

import com.unitesk.requality.nodetype.TestPurpose;

public class TestPurposeDecorator extends TreeNodeStatusDecorator {
  @Override
  protected String getStatusPrefix() {
    return "tp_";
  }

  @Override
  protected String getAttributeName() {
    return TestPurpose.ATTR_STATUS;
  }
}
