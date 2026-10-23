package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.workbench.IWizardRegistry;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/** {@code org.eclipse.ui.internal.dialogs.ImportPage}: the page of the import wizards of the Import dialog. */
public class ImportPage extends ImportExportPage {
  private CategorizedWizardSelectionTree importTree;

  public ImportPage(IWorkbench aWorkbench, IStructuredSelection currentSelection) {
    super(aWorkbench, currentSelection);
  }

  @Override
  protected IWizardRegistry getWizardRegistry() {
    return workbench.getImportWizardRegistry();
  }

  @Override
  protected Composite createTreeViewer(Composite parent) {
    importTree = new CategorizedWizardSelectionTree(getRootCategory(), "&Select an import wizard:");
    Composite importComp = importTree.createControl(parent);
    TreeViewer viewer = importTree.getViewer();
    viewer.addSelectionChangedListener(event -> listSelectionChanged(event.getSelection()));
    viewer.addDoubleClickListener(this::treeDoubleClicked);
    setTreeViewer(viewer);
    return importComp;
  }

  @Override
  protected void updateMessage() {
    setMessage("Choose import wizard.");
    super.updateMessage();
  }
}
