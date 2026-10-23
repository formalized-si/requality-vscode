package com.unitesk.requality.vscode.ui.team.synchronize.actions;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.team.wizards.GlobalSynchronizeWizard;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.actions.GlobalRefreshAction}: Team &gt; Synchronize... (the
 * {@code org.eclipse.team.ui.synchronizeAll} command): the Synchronize wizard that lists the synchronize types.
 */
public class GlobalRefreshAction extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) {
    Shell shell = HandlerUtil.getActiveShell(event);
    if (shell == null)
      shell = Display.getDefault().getActiveShell();
    new WizardDialog(shell, new GlobalSynchronizeWizard()).open();
    return null;
  }
}
