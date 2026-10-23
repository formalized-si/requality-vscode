package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.ui.internal.dialogs.ImportExportWizard}: the wizard of the Import and Export dialogs. Its first page
 * selects a wizard of the category tree; the pages of the selected wizard follow it, and Finish finishes that wizard.
 */
public class ImportExportWizard extends Wizard {
  public static final String IMPORT = "import"; //$NON-NLS-1$
  public static final String EXPORT = "export"; //$NON-NLS-1$

  private IWorkbench workbench;
  private IStructuredSelection selection;
  private ImportExportPage importExportPage;
  private String page = null;
  /** The wizard selected on the first page (its pages follow the first page), or null. */
  private IWizard selectedWizard;

  public ImportExportWizard(String pageId) {
    page = pageId;
  }

  /**
   * {@code ImportExportWizard.performFinish}: the selection page saves its state. The selected wizard, whose pages follow
   * the selection page, finishes first (WizardDialog finishes the nested wizards).
   */
  @Override
  public boolean performFinish() {
    importExportPage.saveWidgetValues();
    return true;
  }

  @Override
  public void addPages() {
    if (page.equals(IMPORT)) {
      importExportPage = new ImportPage(this.workbench, this.selection);
    } else if (page.equals(EXPORT)) {
      importExportPage = new ExportPage(this.workbench, this.selection);
    }
    if (importExportPage != null) {
      addPage(importExportPage);
    }
  }

  /**
   * The wizard selected on the first page, whose pages follow it. It is disposed by the WizardDialog that showed its pages
   * (a wizard that was not shown is not disposed: WorkbenchWizardNode.dispose does nothing).
   */
  void setSelectedWizard(IWizard wizard) {
    selectedWizard = wizard;
  }

  public void init(IWorkbench aWorkbench, IStructuredSelection currentSelection) {
    this.workbench = aWorkbench;
    this.selection = currentSelection;
    if (IMPORT.equals(page)) {
      setWindowTitle("Import"); //$NON-NLS-1$
    } else if (EXPORT.equals(page)) {
      setWindowTitle("Export"); //$NON-NLS-1$
    }
    // the Back and Next buttons are there with the selection page alone (the pages of the wizard follow it)
    setForcePreviousAndNextButtons(true);
    setNeedsProgressMonitor(true);
  }

}
