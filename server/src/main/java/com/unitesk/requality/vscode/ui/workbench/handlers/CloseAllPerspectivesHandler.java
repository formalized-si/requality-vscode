package com.unitesk.requality.vscode.ui.workbench.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;

/**
 * {@code org.eclipse.ui.internal.handlers.CloseAllPerspectivesHandler}: the default handler of
 * {@code org.eclipse.ui.window.closeAllPerspectives} (Window &gt; Perspective &gt; Close All Perspectives).
 */
public class CloseAllPerspectivesHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) {
    IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
    if (window == null)
      return null;
    IWorkbenchPage page = window.getActivePage();
    if (page != null)
      page.closeAllPerspectives(true, true);
    return null;
  }
}
