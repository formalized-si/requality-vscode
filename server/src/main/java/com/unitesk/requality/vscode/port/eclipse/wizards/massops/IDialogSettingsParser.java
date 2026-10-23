package com.unitesk.requality.vscode.port.eclipse.wizards.massops;

import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.core.model.TreeNode;

public interface IDialogSettingsParser {

  public Object parse();

  public void setDialogSettings(IDialogSettings dialogSettings);

  public IDialogSettings getDialogSettings();

  public TreeNode getRoot(TreeNode superRoot);
}
