package com.unitesk.requality.vscode.ui.workbench.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry.IPerspectiveDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;

/**
 * {@code org.eclipse.ui.internal.handlers.ResetPerspectiveHandler}: the default handler of
 * {@code org.eclipse.ui.window.resetPerspective} (Window &gt; Perspective &gt; Reset Perspective...). VSCODE: the
 * perspectives are never customized, so the question only offers the reset to the defaults.
 */
public class ResetPerspectiveHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) {
    IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
    if (window == null)
      return null;
    IWorkbenchPage page = window.getActivePage();
    if (page == null)
      return null;
    IPerspectiveDescriptor descriptor = page.getPerspective();
    if (descriptor == null)
      return null;
    String message = "Reset the " + descriptor.getLabel() + " perspective to its defaults?";
    MessageDialog dialog = new MessageDialog(window.getShell(), "Reset Perspective", null, message,
        MessageDialog.QUESTION, 0, "&Reset Perspective", IDialogConstants.NO_LABEL);
    if (dialog.open() == 0)
      page.resetPerspective();
    return null;
  }
}
