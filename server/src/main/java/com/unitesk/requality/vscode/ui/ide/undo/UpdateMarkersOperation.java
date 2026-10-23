package com.unitesk.requality.vscode.ui.ide.undo;

import java.util.Map;

import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/**
 * {@code org.eclipse.ui.ide.undo.UpdateMarkersOperation}: sets the attributes of markers (merged into or replacing the
 * existing ones); the undo sets the replaced attributes back.
 */
public class UpdateMarkersOperation extends AbstractMarkersOperation {
  private final boolean mergeAttributes;

  public UpdateMarkersOperation(IMarker marker, Map<String, Object> attributes, String name, boolean mergeAttributes) {
    super(new IMarker[] { marker }, null, attributes, name);
    this.mergeAttributes = mergeAttributes;
  }

  public UpdateMarkersOperation(IMarker[] markers, Map<String, Object> attributes, String name,
      boolean mergeAttributes) {
    super(markers, null, attributes, name);
    this.mergeAttributes = mergeAttributes;
  }

  @Override
  protected void doExecute(IProgressMonitor monitor, IAdaptable info) throws CoreException {
    monitor.beginTask("", 100);
    monitor.setTaskName(UndoMessages.MarkerOperation_UpdateProgress);
    updateMarkers(100, monitor, mergeAttributes);
    monitor.done();
  }

  @Override
  protected void doUndo(IProgressMonitor monitor, IAdaptable info) throws CoreException {
    doExecute(monitor, info);
  }

  @Override
  protected IStatus getBasicUndoStatus() {
    return getMarkerUpdateStatus();
  }

  @Override
  protected IStatus getBasicRedoStatus() {
    return getMarkerUpdateStatus();
  }
}
