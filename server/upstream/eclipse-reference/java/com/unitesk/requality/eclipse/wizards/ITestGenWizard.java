package com.unitesk.requality.eclipse.wizards;

import java.util.Map;

import org.eclipse.ui.INewWizard;
import com.unitesk.requality.core.model.TreeDB;

public interface ITestGenWizard extends INewWizard {
  public String getTargetQID();

  public void setTreeDB(TreeDB treeDB);

  public Map<String, String> getSettings();

  public void setTargetQID(String userFriendlyName, boolean local);
}
