package com.unitesk.requality.core.operation.create;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;

public class CreateRequirementOperation extends CreateElementOperation {
  private static CreateElementOperation one = new CreateRequirementOperation();
  public static CreateElementOperation getOne() {
    return one;
  }  

  @Override
  public String[] getElementTypes() {
    return new String[] {Requirement.TYPE_NAME};
  };

  @Override
  protected void setElementSpecificAttributes(TreeNode nn) {
    // TODO Auto-generated method stub

  }
}
