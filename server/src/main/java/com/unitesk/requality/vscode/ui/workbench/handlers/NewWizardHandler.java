package com.unitesk.requality.vscode.ui.workbench.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.actions.NewWizardAction;

/**
 * {@code org.eclipse.ui.internal.handlers.WizardHandler.New}: the default handler of {@code org.eclipse.ui.newWizard}
 * (File > New > Other..., Ctrl+N): the New wizard selection dialog for the current selection.
 */
public class NewWizardHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) {
    IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
    if (window == null)
      window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    new NewWizardAction(window).run();
    return null;
  }
}
