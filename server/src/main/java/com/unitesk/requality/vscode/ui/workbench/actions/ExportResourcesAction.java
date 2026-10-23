package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.dialogs.ImportExportWizard;

/**
 * {@code org.eclipse.ui.actions.ExportResourcesAction} ("E&xport..."): opens the Export dialog ({@code
 * org.eclipse.ui.internal.dialogs.ImportExportWizard}) with the selection of the action.
 */
public class ExportResourcesAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.ExportResourcesAction";

  public ExportResourcesAction(Supplier<Shell> shell) {
    super("E&xport...", shell);
    setToolTipText("Export");
    setId(ID);
    setActionDefinitionId(ActionFactory.EXPORT.getCommandId());
  }

  @Override
  public void run() {
    Shell s = shell.get();
    if (s == null)
      s = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
    ImportExportWizard wizard = new ImportExportWizard(ImportExportWizard.EXPORT);
    wizard.init(PlatformUI.getWorkbench(), getStructuredSelection());
    WizardDialog dialog = new WizardDialog(s, wizard);
    dialog.open();
  }
}
