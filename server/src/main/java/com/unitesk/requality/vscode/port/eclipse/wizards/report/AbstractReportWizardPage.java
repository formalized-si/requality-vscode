package com.unitesk.requality.vscode.port.eclipse.wizards.report;

import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.core.model.TreeNode;

public abstract class AbstractReportWizardPage extends WizardPage implements IReportWizardPage {

  private TreeNode report;

  @Override
  public void init(TreeNode report) {
    this.report = report;
  };

  protected AbstractReportWizardPage(String pageName, TreeNode report) {
    super(pageName);
    init(report);
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.vscode.port.eclipse.wizards.report.IReportWizardPage#getReportNode()
   */
  @Override
  public TreeNode getReportNode() {
    return report;
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.vscode.port.eclipse.wizards.report.IReportWizardPage#saveChanges()
   */
  @Override
  public abstract void saveChanges();
}
