package com.unitesk.requality.vscode.ui.ide.undo;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

/**
 * {@code org.eclipse.ui.ide.undo.AbstractCopyOrMoveResourcesOperation}: the base of the copy and move operations, with
 * the destination paths of the resources.
 */
abstract class AbstractCopyOrMoveResourcesOperation extends AbstractResourcesOperation {
  protected IPath[] destinationPaths = null;
  protected IPath destination = null;

  AbstractCopyOrMoveResourcesOperation(IResource[] resources, IPath[] destinationPaths, String label) {
    super(resources, label);
    if (this.resources == null || destinationPaths == null) {
      throw new IllegalArgumentException("The resource and destination paths may not be null");
    }
    if (this.resources.length != resources.length) {
      throw new IllegalArgumentException(
          "The resource list contained descendants that cannot be moved to separate destination paths");
    }
    if (this.resources.length != destinationPaths.length) {
      throw new IllegalArgumentException("The resource and destination paths must be the same length");
    }
    for (int i = 0; i < this.resources.length; i++) {
      if (this.resources[i] == null) {
        throw new IllegalArgumentException("The resources array may not contain null resources");
      }
      if (destinationPaths[i] == null) {
        throw new IllegalArgumentException("The destination paths array may not contain null paths");
      }
    }
    this.destinationPaths = destinationPaths;
  }

  AbstractCopyOrMoveResourcesOperation(IResource[] resources, IPath destinationPath, String label) {
    super(resources, label);
    destination = destinationPath;
  }

  AbstractCopyOrMoveResourcesOperation(IResource[] resources, String label) {
    super(resources, label);
  }

  protected IStatus computeMoveOrCopyStatus() {
    if (resources == null) {
      markInvalid();
      return getErrorStatus(UndoMessages.AbstractResourcesOperation_NotEnoughInfo);
    }
    for (int i = 0; i < resources.length; i++) {
      IResource resource = resources[i];
      if (!resource.exists()) {
        markInvalid();
        return getErrorStatus(UndoMessages.AbstractCopyOrMoveResourcesOperation_ResourceDoesNotExist);
      }
      if (!isDestinationPathValid(resource, i)) {
        markInvalid();
        return getErrorStatus(UndoMessages.AbstractCopyOrMoveResourcesOperation_SameNameOrLocation);
      }
      IStatus status = getWorkspace().validateName(getProposedName(resource, i), resource.getType());
      if (status.getSeverity() == IStatus.ERROR) {
        markInvalid();
      }
      if (!status.isOK()) {
        return status;
      }
    }
    return Status.OK_STATUS;
  }

  protected IPath getDestinationPath(IResource resource, int index) {
    if (destinationPaths != null) {
      return destinationPaths[index];
    }
    return destination.append(resource.getName());
  }

  @Override
  protected void appendDescriptiveText(StringBuffer text) {
    super.appendDescriptiveText(text);
    text.append(" destination: ");
    text.append(destination);
    text.append(", destinationPaths: ");
    text.append(destinationPaths == null ? null : java.util.Arrays.asList(destinationPaths));
    text.append('\'');
  }

  protected boolean isDestinationPathValid(IResource resource, int index) {
    return !resource.getFullPath().equals(getDestinationPath(resource, index));
  }

  protected String getProposedName(IResource resource, int index) {
    return getDestinationPath(resource, index).lastSegment();
  }

  @Override
  public IStatus computeExecutionStatus(IProgressMonitor monitor) {
    IStatus status = super.computeExecutionStatus(monitor);
    if (status.isOK()) {
      status = computeMoveOrCopyStatus();
    }
    return status;
  }

  @Override
  public IStatus computeRedoableStatus(IProgressMonitor monitor) {
    IStatus status = super.computeRedoableStatus(monitor);
    if (status.isOK()) {
      status = computeMoveOrCopyStatus();
    }
    return status;
  }

  // VSCODE: the virtual folders, linked resources and path variables of Eclipse are not supported.
}
