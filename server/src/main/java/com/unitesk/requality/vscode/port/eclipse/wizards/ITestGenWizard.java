package com.unitesk.requality.vscode.port.eclipse.wizards;

import java.util.Map;

import com.unitesk.requality.vscode.ui.workbench.INewWizard;
import com.unitesk.requality.core.model.TreeDB;

public interface ITestGenWizard extends INewWizard {
  public String getTargetQID();

  public void setTreeDB(TreeDB treeDB);

  public Map<String, String> getSettings();

  public void setTargetQID(String userFriendlyName, boolean local);
}
