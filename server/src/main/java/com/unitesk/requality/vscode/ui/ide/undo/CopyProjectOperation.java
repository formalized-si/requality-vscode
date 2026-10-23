package com.unitesk.requality.vscode.ui.ide.undo;

import java.net.URI;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.ResourceSnapshotFactory;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

/**
 * {@code org.eclipse.ui.ide.undo.CopyProjectOperation}: copies a project under a new name and location (null: the
 * default location). The undo deletes the copy, with its contents.
 */
public class CopyProjectOperation extends AbstractCopyOrMoveResourcesOperation {
  private URI projectLocation;
  private final IProject originalProject;
  private IResourceSnapshot<? extends IResource> originalProjectDescription;

  public CopyProjectOperation(IProject project, String name, URI location, String label) {
    super(new IResource[] { project }, Path.fromOSString(name), label);
    if (project == null) {
      throw new IllegalArgumentException("The project may not be null");
    }
    originalProject = project;
    // a null location is the default location of the copy (the location of the workspace root)
    projectLocation = location;
  }

  private IProject getProposedProjectHandle() {
    if (destination.segmentCount() == 1) {
      return getWorkspace().getRoot().getProject(destination.lastSegment());
    }
    return null;
  }

  @Override
  protected IStatus computeMoveOrCopyStatus() {
    IStatus status = Status.OK_STATUS;
    IProject project = getProposedProjectHandle();
    if (project == null) {
      return getErrorStatus(UndoMessages.AbstractResourcesOperation_NotEnoughInfo);
    }
    if (status.isOK()) {
      return super.computeMoveOrCopyStatus();
    }
    return status;
  }

  @Override
  protected void doExecute(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    IProject newProject = copyProject(originalProject, destination, projectLocation, monitor);
    setTargetResources(new IResource[] { newProject });
    setResourceDescriptions(new IResourceSnapshot<?>[0]);
  }

  @Override
  protected void doUndo(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    WorkspaceUndoUtil.delete(resources, monitor, uiInfo, true);
    setTargetResources(new IResource[] { originalProject });
    setResourceDescriptions(new IResourceSnapshot<?>[0]);
  }

  @Override
  protected boolean isDestinationPathValid(IResource resource, int index) {
    return true;
  }

  @Override
  protected String getProposedName(IResource resource, int index) {
    return destination.lastSegment();
  }

  @Override
  public IStatus computeUndoableStatus(IProgressMonitor monitor) {
    IStatus status = super.computeUndoableStatus(monitor);
    if (!status.isOK()) {
      return status;
    }
    if (originalProject == null || !originalProjectDescription.verifyExistence(true)) {
      markInvalid();
      return getErrorStatus(UndoMessages.CopyResourcesOperation_NotAllowedDueToDataLoss);
    }
    if (status.isOK()) {
      status = computeDeleteStatus();
    }
    return status;
  }

  IProject copyProject(IProject project, IPath destinationPath, URI locationURI, IProgressMonitor monitor)
      throws CoreException {
    monitor.setTaskName(UndoMessages.AbstractCopyOrMoveResourcesOperation_copyProjectProgress);
    boolean open = project.isOpen();
    if (!open) {
      project.open(null);
    }
    originalProjectDescription = ResourceSnapshotFactory.fromResource(project);
    IProjectDescription description = project.getDescription();
    description.setName(destinationPath.lastSegment());
    description.setLocationURI(locationURI);
    project.copy(description, IResource.FORCE | IResource.SHALLOW, monitor);
    if (!open) {
      project.close(null);
    }
    return (IProject) getWorkspace().getRoot().findMember(destinationPath);
  }
}
