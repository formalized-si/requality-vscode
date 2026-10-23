package com.unitesk.requality.vscode.ui.ide.undo;

import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.vscode.ui.ide.actions.ReadOnlyStateChecker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

/**
 * {@code org.eclipse.ui.ide.undo.AbstractResourcesOperation}: an operation on resources, which keeps their snapshots
 * (the resources deleted, or overwritten) for the undo.
 */
abstract class AbstractResourcesOperation extends AbstractWorkspaceOperation {
  protected IResourceSnapshot<? extends IResource>[] resourceDescriptions;

  /** True when {@code subResource} is inside {@code superResource} (not the same resource). */
  private static boolean isDescendantOf(IResource subResource, IResource superResource) {
    return !subResource.equals(superResource)
        && superResource.getFullPath().isPrefixOf(subResource.getFullPath());
  }

  AbstractResourcesOperation(IResource[] resources, String label) {
    super(label);
    this.addContext(WorkspaceUndoUtil.getWorkspaceUndoContext());
    setTargetResources(resources);
  }

  AbstractResourcesOperation(IResourceSnapshot<? extends IResource>[] resourceDescriptions, String label) {
    super(label);
    addContext(WorkspaceUndoUtil.getWorkspaceUndoContext());
    setResourceDescriptions(resourceDescriptions);
  }

  protected void delete(IProgressMonitor monitor, IAdaptable uiInfo, boolean deleteContent) throws CoreException {
    setResourceDescriptions(WorkspaceUndoUtil.delete(resources, monitor, uiInfo, deleteContent));
    setTargetResources(new IResource[0]);
  }

  protected void recreate(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    setTargetResources(WorkspaceUndoUtil.recreate(resourceDescriptions, monitor, uiInfo));
    setResourceDescriptions(new IResourceSnapshot<?>[0]);
  }

  protected IStatus computeCreateStatus(boolean allowOverwrite) {
    if (resourceDescriptions == null || resourceDescriptions.length == 0) {
      markInvalid();
      return getErrorStatus(UndoMessages.AbstractResourcesOperation_NotEnoughInfo);
    }
    for (IResourceSnapshot<? extends IResource> resourceDescription : resourceDescriptions) {
      if (resourceDescription == null || !resourceDescription.isValid()) {
        markInvalid();
        return getErrorStatus(UndoMessages.AbstractResourcesOperation_InvalidRestoreInfo);
      } else if (!allowOverwrite && resourceDescription.verifyExistence(false)) {
        markInvalid();
        return getErrorStatus(UndoMessages.AbstractResourcesOperation_ResourcesAlreadyExist);
      }
    }
    return Status.OK_STATUS;
  }

  protected IStatus computeDeleteStatus() {
    if (resources == null || resources.length == 0) {
      markInvalid();
      return getErrorStatus(UndoMessages.AbstractResourcesOperation_NotEnoughInfo);
    }
    if (!resourcesExist()) {
      markInvalid();
      return getErrorStatus(UndoMessages.AbstractResourcesOperation_ResourcesDoNotExist);
    }
    return checkReadOnlyResources(resources);
  }

  /**
   * Checks the specified resources for read only state, and returns a status indicating whether the resources can be
   * deleted.
   */
  IStatus checkReadOnlyResources(IResource[] resourcesToCheck) {
    // check read only status if we are permitted to consult the user
    if (!quietCompute) {
      // {@code IDEWorkbenchMessages.DeleteResourceAction_title1} and {@code DeleteResourceAction_readOnlyQuestion}
      ReadOnlyStateChecker checker = new ReadOnlyStateChecker(getShell(null), "Confirm Resource Delete",
          "''{0}'' is read-only. Do you still wish to delete it?");
      checker.setIgnoreLinkedResources(true);
      IResource[] approvedResources = checker.checkReadOnlyResources(resourcesToCheck);
      if (approvedResources.length == 0) {
        // consider this a cancelled redo
        return Status.CANCEL_STATUS;
      }
      // redefine the redo to only include the approved ones
      setTargetResources(approvedResources);
    }
    return Status.OK_STATUS;
  }

  protected void setResourceDescriptions(IResourceSnapshot<? extends IResource>[] descriptions) {
    if (descriptions == null) {
      resourceDescriptions = new IResourceSnapshot<?>[0];
    } else {
      resourceDescriptions = descriptions;
    }
  }

  @Override
  protected void appendDescriptiveText(StringBuffer text) {
    super.appendDescriptiveText(text);
    text.append(" resourceDescriptions: ");
    text.append(resourceDescriptions == null ? null : java.util.Arrays.asList(resourceDescriptions));
    text.append('\'');
  }

  /** Nested resources are removed from the targets: the operation on a folder covers its members. */
  @Override
  protected void setTargetResources(IResource[] targetResources) {
    Set<IResource> subResources = new HashSet<>();
    for (IResource subResource : targetResources) {
      for (IResource superResource : targetResources) {
        if (isDescendantOf(subResource, superResource) && !subResources.contains(subResource)) {
          subResources.add(subResource);
        }
      }
    }
    IResource[] nestedResourcesRemoved = new IResource[targetResources.length - subResources.size()];
    int j = 0;
    for (IResource targetResource : targetResources) {
      if (!subResources.contains(targetResource)) {
        nestedResourcesRemoved[j] = targetResource;
        j++;
      }
    }
    super.setTargetResources(nestedResourcesRemoved);
  }
}
