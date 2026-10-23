package com.unitesk.requality.vscode.ui.workbench.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.dialogs.CustomizePerspectiveDialog;

/**
 * {@code org.eclipse.ui.internal.handlers.EditActionSetsHandler}: the default handler of {@code
 * org.eclipse.ui.window.customizePerspective} (Window &gt; Perspective &gt; Customize Perspective...). It opens the
 * {@link CustomizePerspectiveDialog} of the current perspective; there is none when no perspective is shown.
 */
public class CustomizePerspectiveHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) {
    IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
    if (window == null)
      return null;
    IWorkbenchPage page = window.getActivePage();
    if (page == null || page.getPerspective() == null)
      return null;
    new CustomizePerspectiveDialog(window.getShell(), page).open();
    return null;
  }
}
