package com.unitesk.requality.vscode.services;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.ClientProgressMonitor;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.ui.compare.CompareLinks;
import com.unitesk.requality.vscode.ui.resources.FileHistory;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/** Workspace folders, project discovery and progress cancellation. */
public final class WorkspaceService implements Services.Service {
  @Override
  public void register(RequalityServer server) {
    server.rpc().onRequest("workspace/projects", p -> {
      server.requireInitialized();
      return server.workspace().describe();
    });
    server.rpc().onRequest("workspace/setFolders", p -> {
      server.requireInitialized();
      List<String> folders = new ArrayList<>();
      p.getAsJsonArray("folders").forEach(e -> folders.add(e.getAsString()));
      server.workspace().setFolders(folders);
      return null;
    });
    server.rpc().onRequest("workspace/rescan", p -> {
      server.requireInitialized();
      server.workspace().rescan();
      return null;
    });
    // The active text editor of VS Code shows another file (the parts linked with the editor follow it).
    server.rpc().onNotificationOrdered("editor/activeTextEditor", p -> com.unitesk.requality.vscode.ui.swt.widgets.Display
        .getDefault().asyncExec(() -> com.unitesk.requality.vscode.ui.workbench.ActiveTextEditor.changed(Nodes.optString(p, "path", null))));
    // The diff editor of VS Code that became active or was closed shows the two sides of a compare opened from a view
    // (the sides are echoed as the client/openDiff request gave them, by path or by id): see CompareLinks.
    server.rpc().onNotificationOrdered("editor/activeDiff", p -> Display.getDefault().asyncExec(() -> CompareLinks.diffActivated(
        diffSide(p, "left"), diffSide(p, "right"))));
    server.rpc().onNotificationOrdered("editor/diffClosed", p -> Display.getDefault().asyncExec(() -> CompareLinks.diffClosed(
        diffSide(p, "left"), diffSide(p, "right"))));
    // A text editor of VS Code is about to save a file: as an Eclipse editor saves with IResource.KEEP_HISTORY, the
    // contents being replaced become a state of the local history of the workspace file.
    server.rpc().onRequest("resources/willSave", p -> {
      server.requireInitialized();
      String path = Nodes.string(p, "path");
      if (path == null)
        return false;
      IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(new File(path).getAbsolutePath()));
      return file != null && FileHistory.keepBeforeSave(file);
    });
    // File > Import > Existing Projects into Workspace (WizardProjectsImportPage): the projects under a root
    // directory, and importing the chosen ones (projects removed from the workspace come back this way).
    server.rpc().onRequest("workspace/findProjects", p -> {
      server.requireInitialized();
      com.google.gson.JsonArray r = new com.google.gson.JsonArray();
      for (java.io.File d : server.workspace().findProjects(new java.io.File(Nodes.string(p, "root")))) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("path", d.getAbsolutePath());
        String name = com.unitesk.requality.tool.CoreUtils.getProjectName(d);
        o.addProperty("name", name == null ? d.getName() : name);
        o.addProperty("inWorkspace", server.workspace().project(d) != null
            || server.workspace().anyProject(d.getName()) != null);
        r.add(o);
      }
      return r;
    });
    server.rpc().onRequest("workspace/importProjects", p -> {
      server.requireInitialized();
      com.google.gson.JsonArray imported = new com.google.gson.JsonArray();
      for (com.google.gson.JsonElement e : p.getAsJsonArray("paths")) {
        java.io.File d = new java.io.File(e.getAsString());
        if (com.unitesk.requality.vscode.core.Workspace.isEclipseProject(d) && server.workspace().project(d) == null
            && server.workspace().anyProject(d.getName()) == null) {
          server.workspace().addProject(d);
          imported.add(d.getAbsolutePath());
        }
      }
      return imported;
    });
    server.rpc().onNotification("progress/cancel", p -> ClientProgressMonitor.cancel(Nodes.string(p, "token")));
  }

  /** A side of an {@code editor/activeDiff} or {@code editor/diffClosed} notification, or null. */
  private static com.google.gson.JsonObject diffSide(com.google.gson.JsonObject p, String side) {
    return p.has(side) && p.get(side).isJsonObject() ? p.getAsJsonObject(side) : null;
  }
}
