package com.unitesk.requality.eclipse.wizards.report;

import com.unitesk.requality.core.model.TreeNode;

public interface IReportWizardPage {

  public void init(TreeNode report);

  public TreeNode getReportNode();

  public void saveChanges();
}
