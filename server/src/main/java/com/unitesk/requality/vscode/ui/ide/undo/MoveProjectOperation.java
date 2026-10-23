package com.unitesk.requality.vscode.ui.ide.undo;

import java.net.URI;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/**
 * {@code org.eclipse.ui.ide.undo.MoveProjectOperation}: moves a project to another location (null: the default
 * location). The undo moves it back to the location it had.
 */
public class MoveProjectOperation extends AbstractCopyOrMoveResourcesOperation {
  private URI projectLocation;

  public MoveProjectOperation(IProject project, URI location, String label) {
    super(new IResource[] { project }, label);
    if (project == null) {
      throw new IllegalArgumentException("The project may not be null");
    }
    projectLocation = location;
  }

  @Override
  protected boolean isDestinationPathValid(IResource resource, int index) {
    return true;
  }

  @Override
  protected String getProposedName(IResource resource, int index) {
    return getProject().getName();
  }

  private IProject getProject() {
    return (IProject) resources[0];
  }

  @Override
  protected void doExecute(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    projectLocation = moveProject(getProject(), projectLocation, monitor);
    setResourceDescriptions(new IResourceSnapshot<?>[0]);
  }

  @Override
  protected void doUndo(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    doExecute(monitor, uiInfo);
  }

  @Override
  public IStatus computeUndoableStatus(IProgressMonitor monitor) {
    IStatus status = super.computeUndoableStatus(monitor);
    if (status.isOK()) {
      status = computeMoveOrCopyStatus();
    }
    return status;
  }

  /** Moves the project; returns the location it had (the location of the undo). */
  URI moveProject(IProject project, URI locationURI, IProgressMonitor monitor) throws CoreException {
    monitor.setTaskName(UndoMessages.AbstractCopyOrMoveResourcesOperation_moveProjectProgress);
    // VSCODE: the location the project has now (its folder) is the one the undo moves it back to.
    URI newDestinationURI = project.getLocationURI();
    IProjectDescription description = project.getDescription();
    description.setLocationURI(locationURI);
    project.move(description, IResource.FORCE | IResource.SHALLOW, monitor);
    return newDestinationURI;
  }
}
