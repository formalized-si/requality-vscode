package com.unitesk.requality.core.operation.create;

import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class CreateCommentOperation extends CreateElementOperation {
  private static CreateElementOperation one =  new CreateCommentOperation();
  public static CreateElementOperation getOne() {
    return one;
  }
  @Override
  public Pair<String, Boolean> getNewIdentifier(TreeNode par, String type,boolean filter) {
    if(!filter)
      return null;
    return new Pair<>(RequalityCLI.getNextId(par, type, RequalityCLI.detectPrefix(par, type, filter)),true);
  };
  @Override
  public String[] getElementTypes() {
    return new String[] {Comment.TYPE_NAME};
  }

  @Override
  protected void setElementSpecificAttributes(TreeNode nn) {
    nn.attrs().setConstAttribute(Comment.ATTR_AUTHOR, System.getProperty("user.name", ""), Flag.HIDDEN);
  }
  


}
