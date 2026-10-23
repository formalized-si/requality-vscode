package com.unitesk.requality.vscode.ui.ide.undo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.ResourceSnapshotFactory;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/**
 * {@code org.eclipse.ui.ide.undo.CopyResourcesOperation}: copies resources to destinations. The undo deletes the copies
 * and restores what the copy overwrote (from the snapshots), the redo copies again.
 */
public class CopyResourcesOperation extends AbstractCopyOrMoveResourcesOperation {
  IResource[] originalResources;
  IResourceSnapshot<? extends IResource>[] snapshotResourceDescriptions;

  public CopyResourcesOperation(IResource resource, IPath newPath, String label) {
    super(new IResource[] { resource }, new IPath[] { newPath }, label);
    setOriginalResources(new IResource[] { resource });
  }

  public CopyResourcesOperation(IResource[] resources, IPath destinationPath, String label) {
    super(resources, destinationPath, label);
    setOriginalResources(this.resources);
  }

  public CopyResourcesOperation(IResource[] resources, IPath[] destinationPaths, String label) {
    super(resources, destinationPaths, label);
    setOriginalResources(this.resources);
  }

  @Override
  protected void doExecute(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    copy(monitor, uiInfo);
  }

  protected void copy(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    List<IResource> resourcesAtDestination = new ArrayList<>();
    List<IResourceSnapshot<? extends IResource>> overwrittenResources = new ArrayList<>();
    for (int i = 0; i < resources.length; i++) {
      IResourceSnapshot<? extends IResource>[] overwrites = WorkspaceUndoUtil.copy(new IResource[] { resources[i] },
          getDestinationPath(resources[i], i), resourcesAtDestination, monitor, uiInfo, true);
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
  }

  @Override
  protected void doUndo(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    WorkspaceUndoUtil.delete(resources, monitor, uiInfo, true);
    WorkspaceUndoUtil.recreate(resourceDescriptions, monitor, uiInfo);
    setResourceDescriptions(new IResourceSnapshot<?>[0]);
    setTargetResources(originalResources);
    monitor.done();
  }

  @Override
  public IStatus computeUndoableStatus(IProgressMonitor monitor) {
    IStatus status = super.computeUndoableStatus(monitor);
    if (!status.isOK()) {
      return status;
    }
    if (originalResources == null) {
      markInvalid();
      return getErrorStatus(UndoMessages.CopyResourcesOperation_NotAllowedDueToDataLoss);
    }
    for (IResourceSnapshot<? extends IResource> snapshotResourceDescription : snapshotResourceDescriptions) {
      if (!snapshotResourceDescription.verifyExistence(true)) {
        markInvalid();
        return getErrorStatus(UndoMessages.CopyResourcesOperation_NotAllowedDueToDataLoss);
      }
    }
    if (status.isOK()) {
      status = computeDeleteStatus();
    }
    if (status.isOK() && resourceDescriptions != null && resourceDescriptions.length > 0) {
      status = computeCreateStatus(true);
    }
    return status;
  }

  private void setOriginalResources(IResource[] originals) {
    originalResources = originals;
    snapshotResourceDescriptions = new IResourceSnapshot<?>[originals.length];
    for (int i = 0; i < originals.length; i++) {
      snapshotResourceDescriptions[i] = ResourceSnapshotFactory.fromResource(originals[i]);
    }
  }
}
