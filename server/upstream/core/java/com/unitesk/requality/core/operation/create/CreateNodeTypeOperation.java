package com.unitesk.requality.core.operation.create;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.NodeType;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class CreateNodeTypeOperation extends CreateElementOperation {
  private static CreateElementOperation one =  new CreateNodeTypeOperation();
  public static CreateElementOperation getOne() {
    return one;
  }
  @Override
  public Pair<String, Boolean> getNewIdentifier(TreeNode par, String type,boolean filter) {
    if(!filter)
      return null;
    return new Pair<>(RequalityCLI.getNextId(par, type, ""),true);
  };
  @Override
  public String[] getElementTypes() {
    return new String[] {NodeType.TYPE_NAME};
  }

  @Override
  protected void setElementSpecificAttributes(TreeNode nn) {
  }
  


}
