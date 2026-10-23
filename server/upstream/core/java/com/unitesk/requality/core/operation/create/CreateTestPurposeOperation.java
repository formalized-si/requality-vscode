package com.unitesk.requality.core.operation.create;

import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class CreateTestPurposeOperation extends CreateElementOperation {
  private static CreateElementOperation one = new CreateTestPurposeOperation();
  public static CreateElementOperation getOne() {
    return one;
  }  
  @Override
  public Pair<String, Boolean> getNewIdentifier(TreeNode par, String type,boolean filter) {
    return new Pair<>(RequalityCLI.getNextId(par, type, RequalityCLI.detectPrefix(par, type, filter)),true);
  };
  @Override
  public String[] getElementTypes() {
    return new String[] {TestPurpose.TYPE_NAME};
  }
  @Override
  protected void setElementSpecificAttributes(TreeNode nn) {
    nn.attrs().setConstAttribute(TestPurpose.ATTR_AUTHOR, System.getProperty("user.name", ""), Flag.HIDDEN);
    nn.attrs().setConstAttribute(TestPurpose.ATTR_STATUS, "in process", Flag.HIDDEN);
  }
}
