package com.unitesk.requality.vscode.ui.ide.undo;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/**
 * {@code org.eclipse.ui.ide.undo.DeleteResourcesOperation}: deletes resources (files and folders with their history, or
 * projects with or without their contents). The undo recreates them from their snapshots; the content of a project
 * deleted with its contents is not undoable.
 */
public class DeleteResourcesOperation extends AbstractResourcesOperation {
  private boolean deleteContent = false;

  public DeleteResourcesOperation(IResource[] resources, String label, boolean deleteContent) {
    super(resources, label);
    this.deleteContent = deleteContent;
  }

  @Override
  protected void doExecute(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    delete(monitor, uiInfo, deleteContent);
  }

  @Override
  protected void doUndo(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException {
    recreate(monitor, uiInfo);
  }

  @Override
  public IStatus computeExecutionStatus(IProgressMonitor monitor) {
    IStatus status = super.computeExecutionStatus(monitor);
    if (status.isOK()) {
      status = computeDeleteStatus();
    }
    return status;
  }

  @Override
  public IStatus computeUndoableStatus(IProgressMonitor monitor) {
    IStatus status = super.computeUndoableStatus(monitor);
    if (status.isOK()) {
      status = computeCreateStatus(false);
    }
    return status;
  }

  @Override
  public IStatus computeRedoableStatus(IProgressMonitor monitor) {
    IStatus status = super.computeRedoableStatus(monitor);
    if (status.isOK()) {
      status = computeDeleteStatus();
    }
    return status;
  }

  @Override
  protected void appendDescriptiveText(StringBuffer text) {
    super.appendDescriptiveText(text);
    text.append(" deleteContent: ");
    text.append(deleteContent);
    text.append('\'');
  }
}
