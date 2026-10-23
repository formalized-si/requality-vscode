package com.unitesk.requality.vscode.ui.ide.undo;

import java.util.Map;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IMarkerSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.ResourceSnapshotFactory;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/** {@code org.eclipse.ui.ide.undo.CreateMarkersOperation}: creates markers; the undo deletes them. */
public class CreateMarkersOperation extends AbstractMarkersOperation {
  public CreateMarkersOperation(String type, Map<String, Object> attributes, IResource resource, String name) {
    super(null, new IMarkerSnapshot[] { ResourceSnapshotFactory.fromMarkerDetails(type, attributes, resource) }, null,
        name);
  }

  public CreateMarkersOperation(String[] types, Map<String, Object>[] attributes, IResource[] resources, String name) {
    super(null, null, null, name);
    IMarkerSnapshot[] markersToCreate = new IMarkerSnapshot[attributes.length];
    for (int i = 0; i < markersToCreate.length; i++) {
      markersToCreate[i] = ResourceSnapshotFactory.fromMarkerDetails(types[i], attributes[i], resources[i]);
    }
    setMarkerDescriptions(markersToCreate);
  }

  public CreateMarkersOperation(String type, Map<String, Object>[] attributes, IResource[] resources, String name) {
    super(null, null, null, name);
    IMarkerSnapshot[] markersToCreate = new IMarkerSnapshot[attributes.length];
    for (int i = 0; i < markersToCreate.length; i++) {
      markersToCreate[i] = ResourceSnapshotFactory.fromMarkerDetails(type, attributes[i], resources[i]);
    }
    setMarkerDescriptions(markersToCreate);
  }

  @Override
  protected void doExecute(IProgressMonitor monitor, IAdaptable info) throws CoreException {
    monitor.beginTask("", 100);
    monitor.setTaskName(UndoMessages.MarkerOperation_CreateProgress);
    createMarkers(100, monitor);
    monitor.done();
  }

  @Override
  protected void doUndo(IProgressMonitor monitor, IAdaptable info) throws CoreException {
    monitor.beginTask("", 100);
    monitor.setTaskName(UndoMessages.MarkerOperation_DeleteProgress);
    deleteMarkers(100, monitor);
    monitor.done();
  }

  @Override
  protected IStatus getBasicUndoStatus() {
    return getMarkerDeletionStatus();
  }

  @Override
  protected IStatus getBasicRedoStatus() {
    return getMarkerCreationStatus();
  }
}
