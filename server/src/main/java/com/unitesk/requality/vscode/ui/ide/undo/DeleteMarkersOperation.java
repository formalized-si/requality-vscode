package com.unitesk.requality.vscode.ui.ide.undo;

import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/** {@code org.eclipse.ui.ide.undo.DeleteMarkersOperation}: deletes markers; the undo creates them again. */
public class DeleteMarkersOperation extends AbstractMarkersOperation {
  public DeleteMarkersOperation(IMarker[] markers, String name) {
    super(markers, null, null, name);
  }

  @Override
  protected void doExecute(IProgressMonitor monitor, IAdaptable info) throws CoreException {
    monitor.beginTask("", 100);
    monitor.setTaskName(UndoMessages.MarkerOperation_DeleteProgress);
    deleteMarkers(100, monitor);
    monitor.done();
  }

  @Override
  protected void doUndo(IProgressMonitor monitor, IAdaptable info) throws CoreException {
    monitor.beginTask("", 100);
    monitor.setTaskName(UndoMessages.MarkerOperation_CreateProgress);
    createMarkers(100, monitor);
    monitor.done();
  }

  @Override
  protected IStatus getBasicUndoStatus() {
    return getMarkerCreationStatus();
  }

  @Override
  protected IStatus getBasicRedoStatus() {
    return getMarkerDeletionStatus();
  }
}
