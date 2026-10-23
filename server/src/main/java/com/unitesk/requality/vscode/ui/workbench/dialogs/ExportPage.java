package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.workbench.IWizardRegistry;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/** {@code org.eclipse.ui.internal.dialogs.ExportPage}: the page of the export wizards of the Export dialog. */
public class ExportPage extends ImportExportPage {
  private CategorizedWizardSelectionTree exportTree;

  public ExportPage(IWorkbench aWorkbench, IStructuredSelection currentSelection) {
    super(aWorkbench, currentSelection);
  }

  @Override
  protected IWizardRegistry getWizardRegistry() {
    return workbench.getExportWizardRegistry();
  }

  @Override
  protected Composite createTreeViewer(Composite parent) {
    exportTree = new CategorizedWizardSelectionTree(getRootCategory(), "&Select an export wizard:");
    Composite exportComp = exportTree.createControl(parent);
    TreeViewer viewer = exportTree.getViewer();
    viewer.addSelectionChangedListener(event -> listSelectionChanged(event.getSelection()));
    viewer.addDoubleClickListener(this::treeDoubleClicked);
    setTreeViewer(viewer);
    return exportComp;
  }

  @Override
  protected void updateMessage() {
    setMessage("Choose export wizard.");
    super.updateMessage();
  }
}
