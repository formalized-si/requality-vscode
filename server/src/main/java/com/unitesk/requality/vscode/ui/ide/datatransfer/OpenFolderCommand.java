package com.unitesk.requality.vscode.ui.ide.datatransfer;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.OpenFolderCommand}: opens the {@link SmartImportWizard} ("Open Projects
 * from File System...").
 */
public class OpenFolderCommand extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) {
    IWorkbench workbench = PlatformUI.getWorkbench();
    IStructuredSelection structuredSel = HandlerUtil.getCurrentStructuredSelection(event);
    SmartImportWizard wizard = new SmartImportWizard();
    wizard.init(workbench, structuredSel);
    new WizardDialog(HandlerUtil.getActiveShell(event), wizard).open();
    return null;
  }
}
