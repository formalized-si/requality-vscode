package com.unitesk.requality.vscode.ui.ide.undo;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/**
 * {@code org.eclipse.ui.ide.undo.AbstractCreateResourcesOperation}: creates resources from their snapshots; the undo
 * deletes them (never their content).
 */
abstract class AbstractCreateResourcesOperation extends AbstractResourcesOperation {
  AbstractCreateResourcesOperation(IResourceSnapshot<? extends IResource>[] resourceDescriptions, String label) {
    super(resourceDescriptions, label);
  }

  @Override
  protected void doExecute(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    recreate(monitor, uiInfo);
  }

  @Override
  protected void doUndo(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    delete(monitor, uiInfo, false); // never delete content
  }

  @Override
  public IStatus computeExecutionStatus(IProgressMonitor monitor) {
    IStatus status = super.computeExecutionStatus(monitor);
    if (status.isOK()) {
      status = computeCreateStatus(true);
    }
    return status;
  }

  @Override
  public IStatus computeUndoableStatus(IProgressMonitor monitor) {
    IStatus status = super.computeUndoableStatus(monitor);
    if (status.isOK()) {
      status = computeDeleteStatus();
    }
    return status;
  }

  @Override
  public IStatus computeRedoableStatus(IProgressMonitor monitor) {
    IStatus status = super.computeRedoableStatus(monitor);
    if (status.isOK()) {
      status = computeCreateStatus(true);
    }
    return status;
  }
}
