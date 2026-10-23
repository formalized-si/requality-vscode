package com.unitesk.requality.vscode.ui.ide.undo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/**
 * {@code org.eclipse.ui.ide.undo.MoveResourcesOperation}: moves resources to destinations (also the rename of a file or
 * folder). The undo moves them back.
 */
public class MoveResourcesOperation extends AbstractCopyOrMoveResourcesOperation {
  IResource[] originalResources;
  IPath originalDestination;
  IPath[] originalDestinationPaths;

  public MoveResourcesOperation(IResource[] resources, IPath destinationPath, String label) {
    super(resources, destinationPath, label);
    originalResources = this.resources;
    originalDestination = this.destination;
    originalDestinationPaths = this.destinationPaths;
  }

  public MoveResourcesOperation(IResource resource, IPath newPath, String label) {
    super(new IResource[] { resource }, new IPath[] { newPath }, label);
    originalResources = this.resources;
    originalDestination = this.destination;
    originalDestinationPaths = this.destinationPaths;
  }

  @Override
  protected void doExecute(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    move(monitor, uiInfo);
  }

  protected void move(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    List<IResource> resourcesAtDestination = new ArrayList<>();
    List<IPath> undoDestinationPaths = new ArrayList<>();
    List<IResourceSnapshot<? extends IResource>> overwrittenResources = new ArrayList<>();
    for (int i = 0; i < resources.length; i++) {
      IResourceSnapshot<? extends IResource>[] overwrites = WorkspaceUndoUtil.move(new IResource[] { resources[i] },
          getDestinationPath(resources[i], i), resourcesAtDestination, undoDestinationPaths, monitor, uiInfo, true);
      overwrittenResources.addAll(Arrays.asList(overwrites));
    }
    if (resourceDescriptions != null) {
      for (IResourceSnapshot<? extends IResource> resourceDescription : resourceDescriptions) {
        if (resourceDescription != null) {
          resourceDescription.createResource(monitor);
        }
      }
    }
    setResourceDescriptions(overwrittenResources.toArray(new IResourceSnapshot<?>[overwrittenResources.size()]));
    setTargetResources(resourcesAtDestination.toArray(new IResource[resourcesAtDestination.size()]));
    destinationPaths = undoDestinationPaths.toArray(new IPath[undoDestinationPaths.size()]);
    destination = null;
  }

  @Override
  protected void doUndo(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    move(monitor, uiInfo);
    setTargetResources(originalResources);
    this.resourceDescriptions = new IResourceSnapshot<?>[0];
    this.destination = originalDestination;
    this.destinationPaths = originalDestinationPaths;
  }

  @Override
  public IStatus computeUndoableStatus(IProgressMonitor monitor) {
    IStatus status = super.computeUndoableStatus(monitor);
    if (status.isOK()) {
      status = computeMoveOrCopyStatus();
    }
    return status;
  }
}
