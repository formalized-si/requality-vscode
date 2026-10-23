package com.unitesk.requality.vscode.services;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.rpc.RpcException;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.Workbench;

/**
 * Views of plugin.xml rendered by the server in VS Code webview views ({@code org.eclipse.ui.views}): the extension
 * asks for the part when its view is shown ({@code views/open}).
 */
public final class ViewsService implements Services.Service {
  static {
    // Update Processor Tasks (ToDoList) lives in the Requality panel.
    Workbench.setHostView("com.unitesk.requality.views.ToDoList", "requality.todo");
    // Requality Links Explorer (a CommonNavigator) in the Requality side bar.
    Workbench.setHostView("com.unitesk.requality.views.RequalityLinksExplorer", "requality.links");
    // Outline: the outline page of the active editor.
    Workbench.setHostView(com.unitesk.requality.vscode.ui.workbench.ContentOutline.ID, "requality.outline");
    Workbench.registerView(com.unitesk.requality.vscode.ui.workbench.ContentOutline.ID,
        com.unitesk.requality.vscode.ui.workbench.ContentOutline::new);
    // Bookmarks and Tasks (the marker views of the workbench) in the Requality panel.
    Workbench.setHostView(com.unitesk.requality.vscode.ui.ide.views.BookmarkView.ID, "requality.bookmarks");
    Workbench.registerView(com.unitesk.requality.vscode.ui.ide.views.BookmarkView.ID,
        com.unitesk.requality.vscode.ui.ide.views.BookmarkView::new);
    // Team's History view (Show Local History) in the Requality panel.
    Workbench.setHostView(com.unitesk.requality.vscode.ui.team.history.GenericHistoryView.ID, "requality.history");
    Workbench.registerView(com.unitesk.requality.vscode.ui.team.history.GenericHistoryView.ID,
        com.unitesk.requality.vscode.ui.team.history.GenericHistoryView::new);
    // Team's Synchronize view (Team > Synchronize...) in the Requality panel.
    Workbench.setHostView(com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeView.ID, "requality.synchronize");
    Workbench.registerView(com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeView.ID,
        com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeView::new);
    Workbench.setHostView(com.unitesk.requality.vscode.ui.ide.views.TaskList.ID, "requality.tasks");
    Workbench.registerView(com.unitesk.requality.vscode.ui.ide.views.TaskList.ID,
        com.unitesk.requality.vscode.ui.ide.views.TaskList::new);
    // Problems (the problem markers with their severity) in the Requality panel.
    Workbench.setHostView(com.unitesk.requality.vscode.ui.ide.views.ProblemsView.ID, "requality.problems");
    Workbench.registerView(com.unitesk.requality.vscode.ui.ide.views.ProblemsView.ID,
        com.unitesk.requality.vscode.ui.ide.views.ProblemsView::new);
    // Progress (the operations of the workbench) in the Requality panel; the progress manager listens to the jobs.
    Workbench.setHostView(com.unitesk.requality.vscode.ui.workbench.progress.ProgressView.ID, "requality.progress");
    Workbench.registerView(com.unitesk.requality.vscode.ui.workbench.progress.ProgressView.ID,
        com.unitesk.requality.vscode.ui.workbench.progress.ProgressView::new);
    com.unitesk.requality.vscode.ui.workbench.progress.ProgressManager.getInstance();
  }

  /**
   * The workbench opens in the default perspective of the registry (the Requality perspective,
   * ReqEditorPerspectiveFactory, unless Make Default of General &gt; Perspectives chose another one), as a new Eclipse
   * workbench window does: its views (Requality Explorer, Properties, Outline, Links Explorer) are created with the
   * workbench and shown when VS Code shows their views. The Outline's part listener asks every activated editor for its
   * outline page (ReqMarker relies on it).
   */
  public static void createInitialLayout() {
    Display.getDefault().asyncExec(() -> {
      IPerspectiveRegistry registry = PlatformUI.getWorkbench().getPerspectiveRegistry();
      IPerspectiveRegistry.IPerspectiveDescriptor perspective = registry.findPerspectiveWithId(registry.getDefaultPerspective());
      if (perspective == null)
        perspective = registry.findPerspectiveWithId(
            com.unitesk.requality.vscode.port.eclipse.ui.perspective.ReqEditorPerspectiveFactory.ID);
      if (perspective != null)
        Workbench.openInitialPerspective(perspective);
    });
  }

  @Override
  public void register(RequalityServer srv) {
    srv.rpc().onRequest("views/open", p -> {
      srv.requireInitialized();
      String vscodeId = Nodes.string(p, "view");
      String viewId = Workbench.viewOfHost(vscodeId);
      try {
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(viewId, null,
            IWorkbenchPage.VIEW_CREATE);
      } catch (PartInitException e) {
        throw new RpcException(RpcException.NOT_FOUND, e.getMessage());
      }
      return null;
    });
  }
}
