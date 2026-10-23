package com.unitesk.requality.eclipse.wizards.massops;

import org.eclipse.jface.dialogs.IDialogSettings;
import com.unitesk.requality.core.model.TreeNode;

public interface IDialogSettingsParser {

  public Object parse();

  public void setDialogSettings(IDialogSettings dialogSettings);

  public IDialogSettings getDialogSettings();

  public TreeNode getRoot(TreeNode superRoot);
}
