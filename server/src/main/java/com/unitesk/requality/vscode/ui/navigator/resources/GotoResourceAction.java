package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.GotoResourceAction}: asks for a resource of the
 * workspace ({@link GotoResourceDialog}) and selects it in the navigator.
 */
public class GotoResourceAction extends Action {
  protected Shell shell;
  protected TreeViewer viewer;

  public GotoResourceAction(Shell shell, TreeViewer viewer) {
    this.shell = shell;
    this.viewer = viewer;
  }

  /**
   * Collects all resources in the workbench, opens a dialog asking the user to select a resource and changes the
   * selection in the navigator.
   */
  @Override
  public void run() {
    Shell parent = shell != null ? shell : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
    GotoResourceDialog dialog = new GotoResourceDialog(parent, ResourcesPlugin.getWorkspace().getRoot(),
        IResource.FILE | IResource.FOLDER | IResource.PROJECT);
    dialog.open();
    Object[] result = dialog.getResult();
    if (result == null || result.length == 0 || !(result[0] instanceof IResource))
      return;
    viewer.setSelection(new StructuredSelection((IResource) result[0]), true);
  }
}
