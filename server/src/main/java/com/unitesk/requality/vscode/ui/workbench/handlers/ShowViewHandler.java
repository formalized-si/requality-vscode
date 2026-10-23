package com.unitesk.requality.vscode.ui.workbench.handlers;

import java.util.Map;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.ShowViewMenu;
import com.unitesk.requality.vscode.ui.workbench.dialogs.ShowViewDialog;
import com.unitesk.requality.vscode.ui.workbench.views.IViewDescriptor;

/**
 * {@code org.eclipse.ui.handlers.ShowViewHandler}: the default handler of {@code org.eclipse.ui.views.showView}. With the
 * view parameter the view is shown and activated; without it the Show View dialog asks for the views, which are shown in
 * the order of the selection. VSCODE: VS Code has no fast views, so the makeFast parameter has no effect, and the
 * secondary id (several instances of a view) is not supported.
 */
public class ShowViewHandler extends AbstractHandler {
  /** {@code org.eclipse.ui.views.showView.viewId}: the id of the view to show. */
  public static final String VIEW_ID_PARAMETER = "org.eclipse.ui.views.showView.viewId";
  /** {@code org.eclipse.ui.views.showView.secondaryId}: the secondary id of the view. */
  public static final String SECONDARY_ID_PARAMETER = "org.eclipse.ui.views.showView.secondaryId";
  /** {@code org.eclipse.ui.views.showView.makeFast}: "true" to show the view as a fast view. */
  public static final String MAKE_FAST_PARAMETER = "org.eclipse.ui.views.showView.makeFast";

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
    Map<?, ?> parameters = event.getParameters();
    Object id = parameters.get(VIEW_ID_PARAMETER);
    if (!(id instanceof String) || ((String) id).isEmpty()) {
      openOther(window);
      return null;
    }
    showView(window.getActivePage(), (String) id);
    return null;
  }

  /** Opens the Show View dialog and shows the views chosen in it, then publishes the menu items. */
  private static void openOther(IWorkbenchWindow window) throws ExecutionException {
    ShowViewDialog dialog = new ShowViewDialog(window.getShell(), PlatformUI.getWorkbench().getViewRegistry());
    dialog.open();
    if (dialog.getReturnCode() == Window.CANCEL)
      return;
    IWorkbenchPage page = window.getActivePage();
    try {
      for (IViewDescriptor descriptor : dialog.getSelection()) {
        showView(page, descriptor.getId());
        ShowViewMenu.viewOpened(page, descriptor.getId());
      }
    } finally {
      ShowViewMenu.publish();
    }
  }

  private static void showView(IWorkbenchPage page, String viewId) throws ExecutionException {
    try {
      page.showView(viewId, null, IWorkbenchPage.VIEW_ACTIVATE);
    } catch (PartInitException e) {
      throw new ExecutionException("View could not be opened: " + viewId, e);
    }
  }
}
