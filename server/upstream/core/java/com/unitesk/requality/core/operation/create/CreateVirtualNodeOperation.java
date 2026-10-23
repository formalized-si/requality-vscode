package com.unitesk.requality.core.operation.create;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.VirtualNode;

public class CreateVirtualNodeOperation extends CreateElementOperation {
  private static CreateElementOperation one = new CreateVirtualNodeOperation();
  public static CreateElementOperation getOne() {
    return one;
  }  
  @Override
  public String[] getElementTypes() {
    return new String[] {VirtualNode.TYPE_NAME};
  }
  @Override
  protected void setElementSpecificAttributes(TreeNode nn) {}
}
