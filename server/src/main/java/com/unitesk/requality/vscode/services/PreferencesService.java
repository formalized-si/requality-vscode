package com.unitesk.requality.vscode.services;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.rpc.RpcException;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferencePage;
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceDialog;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ProjectRef;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.dialogs.PropertyDialog;

/**
 * The preference pages ({@code org.eclipse.ui.preferencePages}) shown in a PreferenceDialog like Window > Preferences,
 * and the Properties dialog of projects ({@link PropertyDialog}) for the RPC {@code properties/openProject}.
 */
public final class PreferencesService implements Services.Service {
  @Override
  public void register(RequalityServer srv) {
    srv.rpc().onRequest("preferences/open", p -> {
      srv.requireInitialized();
      String pageId = Nodes.optString(p, "page", null);
      List<IConfigurationElement> elements = new ArrayList<>();
      for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.preferencePages"))
        if ("page".equals(e.getName()))
          elements.add(e);
      return Display.getDefault().syncCall(() -> open("Preferences", PropertyDialog.tree(elements), pageId));
    });
    // The VS Code setting workbench.list.openMode changed: the open mode of General follows it.
    srv.rpc().onNotification("settings/listOpenMode", p -> com.unitesk.requality.vscode.core.CoreBootstrap.setListOpenMode(Nodes.optString(p, "value", null)));
    srv.rpc().onRequest("properties/openProject", p -> {
      srv.requireInitialized();
      String id = Nodes.string(p, "project");
      String pageId = Nodes.optString(p, "page", null);
      IProject project = project(srv, id);
      if (project == null)
        throw RpcException.notFound("Project " + id + " is not in the workspace");
      return Display.getDefault().syncCall(() -> {
        PropertyDialog dialog = PropertyDialog.createDialogOn(shell(), pageId, project);
        if (dialog == null)
          throw new RpcException(RpcException.NOT_FOUND, "No pages for Properties for " + project.getName());
        return dialog.open() == Window.OK;
      });
    });
  }

  private static com.unitesk.requality.vscode.ui.swt.widgets.Shell shell() {
    return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
  }

  private static IProject project(RequalityServer srv, String id) {
    String lname = id;
    if (id.startsWith(ExplorerService.PROJECT_PREFIX)) {
      lname = id.substring(ExplorerService.PROJECT_PREFIX.length());
    } else if (id.contains(Nodes.SEP)) {
      TreeNode n = Nodes.find(id);
      lname = n != null ? n.getTreeDB().getSemiLocalName() : Nodes.projectOf(id);
    }
    Workspace.Project wp = srv.workspace().anyProject(lname); // Requality catalogs and other Eclipse projects
    if (wp == null)
      return null;
    com.unitesk.requality.core.model.TreeDB db = Nodes.findDB(lname);
    return db != null ? new ProjectRef(db, wp.dir) : new ProjectRef(lname, wp.dir);
  }

  private static boolean open(String title, List<PropertyDialog.Node> tree, String pageId) {
    List<IPreferencePage> pages = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    PropertyDialog.flatten(tree, "", pages, ids, (IAdaptable) null);
    if (pages.isEmpty())
      throw new RpcException(RpcException.NOT_FOUND, "No pages for " + title);
    PreferenceDialog dialog = new PreferenceDialog(shell(), title, pages);
    if (pageId != null && ids.contains(pageId))
      dialog.setSelectedPage(ids.indexOf(pageId));
    return dialog.open() == Window.OK;
  }
}
