package com.unitesk.requality.vscode.ui.workbench.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.dialogs.PropertyDialog;

/**
 * {@code org.eclipse.ui.internal.handlers.PropertyDialogHandler}: the default handler of
 * {@code org.eclipse.ui.file.properties} (Alt+Enter): the Properties dialog of the first selected element, or the
 * information that it has no property pages.
 */
public class PropertyDialogHandler extends AbstractHandler {
  private final String initialPageId = null;

  @Override
  public Object execute(ExecutionEvent event) {
    PreferenceDialog dialog;
    Object element = null;
    IStructuredSelection currentSelection = HandlerUtil.getCurrentStructuredSelection(event);
    IWorkbenchWindow activeWorkbenchWindow = HandlerUtil.getActiveWorkbenchWindow(event);
    Shell shell;
    element = currentSelection.getFirstElement();
    if (activeWorkbenchWindow != null) {
      shell = activeWorkbenchWindow.getShell();
      dialog = PropertyDialog.createDialogOn(shell, initialPageId, element);
      if (dialog != null)
        dialog.open();
    }
    return null;
  }
}
