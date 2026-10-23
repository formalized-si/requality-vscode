package com.unitesk.requality.core.operation.create;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.TextNode;

public class CreateTextNodeOperation extends CreateElementOperation {
  private static CreateElementOperation one = new CreateTextNodeOperation();
  public static CreateElementOperation getOne() {
    return one;
  }  
  @Override
  public String[] getElementTypes() {
    return new String[] {TextNode.TYPE_NAME};
  }
  @Override
  protected void setElementSpecificAttributes(TreeNode nn) {
    // TODO Auto-generated method stub
  }
}
