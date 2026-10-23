package com.unitesk.requality.vscode.ui.navigator.resources.workbench;

import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.workbench.ResourceExtensionLabelProvider}: the labels of the
 * resources of a navigator content (the "non Requality project provider" of the Requality plug-in): the workbench
 * label, the full path as description, and an error or warning overlay on the image of a resource that has problem
 * markers (on itself or its members).
 */
// VSCODE: the files and folders of the VS Code tree have the images of the file icon theme; the overlay is the
// problems decoration VS Code draws for the diagnostics of the file (the problem markers are published as
// diagnostics by services/ProblemMarkers), so getImage only computes the severity of the overlay.
public class ResourceExtensionLabelProvider extends WorkbenchLabelProvider {
  /** {@code ICommonLabelProvider.init}. */
  public void init(Object aConfig) {
  }

  /** The description (status line text) of the element: the path of a resource relative to the workspace. */
  public String getDescription(Object anElement) {
    if (anElement instanceof IResource)
      return ((IResource) anElement).getFullPath().makeRelative().toString();
    return null;
  }

  /**
   * The overlay of the image (decorateImage): {@link IMarker#SEVERITY_ERROR} or {@link IMarker#SEVERITY_WARNING}
   * for a resource (an open project) with such problems, else -1.
   */
  public int getOverlaySeverity(Object element) {
    IResource resource = element instanceof IResource ? (IResource) element : null;
    if (resource != null && (resource.getType() != IResource.PROJECT || ((IProject) resource).isOpen())) {
      int s = getHighestProblemSeverity(resource);
      if (s == IMarker.SEVERITY_ERROR || s == IMarker.SEVERITY_WARNING)
        return s;
    }
    return -1;
  }

  protected int getHighestProblemSeverity(IResource resource) {
    try {
      return resource.findMaxProblemSeverity(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
    } catch (CoreException e) {
      return -1;
    }
  }

  public void restoreState(IMemento aMemento) {
  }

  public void saveState(IMemento aMemento) {
  }
}
