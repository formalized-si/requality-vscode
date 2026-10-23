package com.unitesk.requality.core.operation.create;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.tool.message.InputValidator;
import com.unitesk.requality.tool.message.RMessages;

public class CreateDocFolderOperation extends CreateElementOperation {
  private static CreateElementOperation one = new CreateDocFolderOperation();
  public static CreateElementOperation getOne() {
    return one;
  }
	  @Override
	  public Pair<String, Boolean> getNewIdentifier(TreeNode par, String type,boolean filter) {
	    if(!filter)
	      return null;
	    Pair<String, Boolean> s = RMessages.openInputDialog(
	            "New Document Folder",
	            "Enter folder name",
	            RequalityCLI.getNextId(par, type, RequalityCLI.detectPrefix(par, type, filter)),
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
	                if (par.findWorkChild(newText) != null) {
	                  return "child with same id is exist";
	                }
	                if (!par.getTreeDB().getTreeLogic().canBeCreatedIn(type, par))
	                  return "This folder cannot contain " + newText;
	                return null;
	              }
	            });
	    return s;
	  };

	  @Override
	  public String[] getElementTypes() {
	    return new String[] {DocFolder.TYPE_NAME};
	  }

	  @Override
	  protected void setElementSpecificAttributes(TreeNode nn) {}

}
