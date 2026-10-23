package com.unitesk.requality.vscode.ui.resources.undo.snapshot;

import java.net.URI;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.core.internal.resources.undo.snapshot.ProjectSnapshot}: a project with its description and its
 * location, so that a project removed from the workspace (its contents kept) can be added again at the same place.
 */
class ProjectSnapshot extends ContainerSnapshot<IProject> {
  private IProjectDescription projectDescription;
  private boolean openOnCreate = true;
  private URI location;

  ProjectSnapshot(IProject project) {
    super(project);
    this.name = project.getName();
    if (project.exists()) {
      location = project.getLocationURI();
      if (project.isOpen()) {
        try {
          this.projectDescription = project.getDescription();
        } catch (CoreException e) {
          // the description is not kept
        }
      } else {
        openOnCreate = false;
      }
    }
  }

  ProjectSnapshot(IProjectDescription projectDescription) {
    super();
    this.projectDescription = projectDescription;
  }

  @Override
  public IProject createResourceHandle() {
    return getWorkspace().getRoot().getProject(getName());
  }

  @Override
  public void createExistentResourceFromHandle(IProgressMonitor monitor) throws CoreException {
    IProject projectHandle = createResourceHandle();
    if (projectHandle.exists()) {
      return;
    }
    if (projectDescription == null) {
      projectHandle.create(monitor);
    } else {
      // the project is created where it was (its folder keeps the contents)
      if (projectDescription.getLocationURI() == null && location != null) {
        projectDescription.setLocationURI(location);
      }
      projectHandle.create(projectDescription, monitor);
    }
    if (openOnCreate) {
      projectHandle.open(IResource.NONE, monitor);
    }
  }

  @Override
  public String getName() {
    if (projectDescription != null) {
      return projectDescription.getName();
    }
    return super.getName();
  }

  @Override
  public boolean verifyExistence(boolean checkMembers) {
    IProject projectHandle = createResourceHandle();
    if (projectHandle.isAccessible()) {
      return super.verifyExistence(checkMembers);
    }
    return super.verifyExistence(false);
  }
}
