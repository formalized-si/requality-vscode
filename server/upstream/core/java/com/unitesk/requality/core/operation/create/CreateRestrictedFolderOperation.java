package com.unitesk.requality.core.operation.create;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.RestrictedFolder;

public class CreateRestrictedFolderOperation extends CreateElementOperation {
	private static String[] types = new String[] {RestrictedFolder.TYPE_NAME};
	@Override
	public String[] getElementTypes() {
		return types;
	}

	@Override
	protected void setElementSpecificAttributes(TreeNode nn) {

	}

}
