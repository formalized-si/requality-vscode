package com.unitesk.requality.vscode.port.eclipse.wizards.report;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.core.model.TreeNode;

public abstract class ReportWizardPagesSet {

  public abstract void saveChanges();

  public abstract void initPages(
      IWorkbench workbench, IStructuredSelection selection, TreeNode node);

  public abstract IReportWizardPage getNextPage();

  public abstract IReportWizardPage getPreviousPage();
}
