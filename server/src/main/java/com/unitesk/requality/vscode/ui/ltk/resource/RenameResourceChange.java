package com.unitesk.requality.vscode.ui.ltk.resource;

import com.unitesk.requality.vscode.ui.ltk.Change;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ProjectRef;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.ltk.core.refactoring.resource.RenameResourceChange}: renames a project at the path to the new name
 * ({@code IProject.move} with the renamed description). A project at a custom location keeps its folder. Its undo
 * renames the project back.
 */
public class RenameResourceChange extends Change {
  private final IPath resourcePath;
  private final String newName;

  public RenameResourceChange(IPath resourcePath, String newName) {
    this.resourcePath = resourcePath;
    this.newName = newName;
  }

  @Override
  public String getName() {
    return "Rename '" + resourcePath.lastSegment() + "' to '" + newName + "'";
  }

  @Override
  public void initializeValidationData(IProgressMonitor pm) {
  }

  @Override
  public RefactoringStatus isValid(IProgressMonitor pm) {
    return new RefactoringStatus();
  }

  @Override
  public Change perform(IProgressMonitor pm) throws CoreException {
    IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(resourcePath.lastSegment());
    String oldName = project.getName();
    IProjectDescription d = project.getDescription();
    if (project instanceof ProjectRef && !((ProjectRef) project).isAtDefaultLocation())
      d.setLocationURI(project.getLocationURI());
    d.setName(newName);
    project.move(d, IResource.FORCE | IResource.SHALLOW, pm);
    return new RenameResourceChange(resourcePath.removeLastSegments(1).append(newName), oldName);
  }

  @Override
  public Object getModifiedElement() {
    return resourcePath;
  }
}
