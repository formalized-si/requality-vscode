package com.unitesk.requality.eclipse.wizards.report;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbench;
import com.unitesk.requality.core.model.TreeNode;

public abstract class ReportWizardPagesSet {

  public abstract void saveChanges();

  public abstract void initPages(
      IWorkbench workbench, IStructuredSelection selection, TreeNode node);

  public abstract IReportWizardPage getNextPage();

  public abstract IReportWizardPage getPreviousPage();
}
