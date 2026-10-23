package com.unitesk.requality.vscode.ui.navigator.resources;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.navigator.ICommonMenuConstants;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.FoldersAsProjectsActionProvider}: the folders of a
 * selection that contain a {@code .project} file: "Import as Project" when the folder is not a project of the
 * workspace, "Go to corresponding project" when it is one.
 */
public class FoldersAsProjectsActionProvider extends CommonActionProvider {
  private CommonViewer viewer;

  @Override
  public void init(ICommonActionExtensionSite aSite) {
    super.init(aSite);
    viewer = (CommonViewer) aSite.getStructuredViewer();
  }

  @Override
  public void fillContextMenu(IMenuManager aMenu) {
    if (getContext() == null || !(getContext().getSelection() instanceof IStructuredSelection))
      return;
    IStructuredSelection selection = (IStructuredSelection) getContext().getSelection();
    if (selection.isEmpty())
      return;
    Map<String, IProject> projects = projectsMap();
    List<IProject> existing = new LinkedList<>();
    List<IFolder> nonexisting = new LinkedList<>();
    for (Object object : selection.toList()) {
      if (!(object instanceof IFolder))
        return;
      IFolder folder = (IFolder) object;
      if (!folder.getFile(IProjectDescription.DESCRIPTION_FILE_NAME).exists())
        return;
      IProject project = projects.get(folder.getLocation().toOSString());
      if (project != null)
        existing.add(project);
      else
        nonexisting.add(folder);
      if (existing.size() > 0 && nonexisting.size() > 0)
        return;
    }
    if (existing.size() > 0) {
      SelectProjectForFolderAction action = new SelectProjectForFolderAction(existing, this.viewer);
      aMenu.appendToGroup(ICommonMenuConstants.GROUP_OPEN, action);
    } else {
      OpenFolderAsProjectAction action = new OpenFolderAsProjectAction(nonexisting, this.viewer);
      aMenu.prependToGroup(ICommonMenuConstants.GROUP_PORT, action);
    }
  }

  /** The projects of the workspace by their location. */
  private static Map<String, IProject> projectsMap() {
    Map<String, IProject> r = new HashMap<>();
    for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects())
      if (p.getLocation() != null)
        r.put(p.getLocation().toOSString(), p);
    return r;
  }
}
