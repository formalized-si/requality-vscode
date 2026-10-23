package com.unitesk.requality.vscode.ui.navigator.resources;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.OpenFolderAsProjectAction}: imports the folders as
 * projects of the workspace (File &gt; Import &gt; Existing Projects) and selects them. A folder already a project is
 * not imported twice.
 */
public class OpenFolderAsProjectAction extends Action {
  private final Collection<IFolder> folders;
  private final CommonViewer viewer;

  public OpenFolderAsProjectAction(Collection<IFolder> folder, CommonViewer viewer) {
    super(folder.size() > 1 ? "Impor&t as Projects" : "Impor&t as Project");
    this.folders = folder;
    this.viewer = viewer;
    setDescription("Imports this existing Project into Workspace");
    setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJ_PROJECT));
  }

  public OpenFolderAsProjectAction(IFolder folder, CommonViewer viewer) {
    this(Collections.singleton(folder), viewer);
  }

  @Override
  public void run() {
    // VSCODE: the import runs in the UI thread (the workspace emulation loads the project on its own thread).
    List<IProject> imported = new ArrayList<>();
    for (IFolder folder : folders) {
      IProject project = importFolder(folder);
      if (project != null)
        imported.add(project);
    }
    if (!imported.isEmpty())
      viewer.setSelection(new StructuredSelection(imported));
  }

  /** The project of the folder once imported (it has a {@code .project}); null when it is not one. */
  static IProject importFolder(IFolder folder) {
    File dir = new File(folder.getLocation().toOSString());
    Workspace ws = ServerContext.server().workspace();
    if (!Workspace.isEclipseProject(dir) || ws.project(dir) != null || ws.anyProject(dir.getName()) != null)
      return null;
    ws.addProject(dir);
    return ResourcesPlugin.getWorkspace().getRoot().getProject(dir.getName());
  }
}
