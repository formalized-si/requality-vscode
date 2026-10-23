package com.unitesk.requality.core.operation.create;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.ReportFolder;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.tool.message.InputValidator;
import com.unitesk.requality.tool.message.RMessages;

public class CreateReportFolderOperation extends CreateElementOperation {
  private static CreateElementOperation one = new CreateReportFolderOperation();
  public static CreateElementOperation getOne() {
    return one;
  }
  @Override
  public Pair<String, Boolean> getNewIdentifier(TreeNode parent, String type,boolean filter){ 
    if(!filter)
      return null;
    return RMessages.openInputDialog(
            "New Report Folder",
            "Enter folder name",
            RequalityCLI.getNextId(parent, type, ReportFolder.ID_PREFIX),
            new InputValidator() {
              @Override
              public String isValid(String newText) {
                newText = newText.trim();
                if (newText.length() == 0) {
                  return "enter folder name";
                }
                if (newText.lastIndexOf("/") != -1) {
                  return "folder name can't contains '/'";
                }
                if (parent.findWorkChild(newText) != null) {
                  return "child with same id is exist";
                }
                if (!parent.getTreeDB().getTreeLogic().canBeCreatedIn(type, parent))
                  return "This folder cannot contain " + newText;
                return null;
              }
            });
  };

  @Override
  public String[] getElementTypes() {
    return new String[] {ReportFolder.TYPE_NAME};
  }

  @Override
  protected void setElementSpecificAttributes(TreeNode nn) {
    // TODO Auto-generated method stub

  }
}
