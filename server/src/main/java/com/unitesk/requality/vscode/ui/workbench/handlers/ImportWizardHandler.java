package com.unitesk.requality.vscode.ui.workbench.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.dialogs.ImportExportWizard;

/**
 * {@code org.eclipse.ui.internal.handlers.WizardHandler.Import}: the default handler of {@code org.eclipse.ui.file.import}
 * (File > Import...): the Import wizard selection dialog for the current selection.
 */
public class ImportWizardHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) {
    IStructuredSelection selection = HandlerUtil.getCurrentStructuredSelection(event);
    Shell shell = HandlerUtil.getActiveShell(event);
    if (shell == null)
      shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
    ImportExportWizard wizard = new ImportExportWizard(ImportExportWizard.IMPORT);
    wizard.init(PlatformUI.getWorkbench(), selection);
    new WizardDialog(shell, wizard).open();
    return null;
  }
}
