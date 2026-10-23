package com.unitesk.requality.vscode.ui.ide.undo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IMarkerSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.ResourceSnapshotFactory;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

/**
 * {@code org.eclipse.ui.ide.undo.AbstractMarkersOperation}: the base of the operations on markers. The markers deleted
 * are kept as snapshots (their type and attributes), the attributes replaced are kept for the undo.
 */
abstract class AbstractMarkersOperation extends AbstractWorkspaceOperation {
  IMarkerSnapshot[] markerDescriptions;
  IMarker[] markers;
  Map<String, Object>[] attributes;

  @SuppressWarnings("unchecked")
  AbstractMarkersOperation(IMarker[] markers, IMarkerSnapshot[] markerDescriptions, Map<String, Object> attributes,
      String name) {
    super(name);
    this.markers = markers;
    this.attributes = null;
    if (attributes != null && markers != null) {
      if (markers.length > 1) {
        this.attributes = new Map[markers.length];
        for (int i = 0; i < markers.length; i++) {
          this.attributes[i] = new HashMap<>(attributes);
        }
      } else {
        this.attributes = new Map[] { attributes };
      }
    }
    setMarkerDescriptions(markerDescriptions);
  }

  protected void deleteMarkers(int work, IProgressMonitor monitor) throws CoreException {
    if (markers == null || markers.length == 0) {
      monitor.worked(work);
      return;
    }
    int markerWork = work / markers.length;
    markerDescriptions = new IMarkerSnapshot[markers.length];
    for (int i = 0; i < markers.length; i++) {
      markerDescriptions[i] = ResourceSnapshotFactory.fromMarker(markers[i]);
      markers[i].delete();
      monitor.worked(markerWork);
    }
    markers = new IMarker[0];
  }

  protected void createMarkers(int work, IProgressMonitor monitor) throws CoreException {
    if (markerDescriptions == null || markerDescriptions.length == 0) {
      monitor.worked(work);
      return;
    }
    int markerWork = work / markerDescriptions.length;
    markers = new IMarker[markerDescriptions.length];
    for (int i = 0; i < markerDescriptions.length; i++) {
      markers[i] = markerDescriptions[i].createMarker();
      monitor.worked(markerWork);
    }
  }

  protected void updateMarkers(int work, IProgressMonitor monitor, boolean mergeAttributes) throws CoreException {
    if (attributes == null || markers == null || attributes.length != markers.length || markers.length == 0) {
      monitor.worked(work);
      return;
    }
    int markerWork = work / markers.length;
    for (int i = 0; i < markers.length; i++) {
      if (mergeAttributes) {
        Map<String, Object> oldAttributes = markers[i].getAttributes();
        int increment = markerWork / attributes[i].size();
        Map<String, Object> replacedAttributes = new HashMap<>();
        for (Iterator<String> iter = attributes[i].keySet().iterator(); iter.hasNext();) {
          String key = iter.next();
          Object val = attributes[i].get(key);
          markers[i].setAttribute(key, val);
          replacedAttributes.put(key, oldAttributes.get(key));
          monitor.worked(increment);
        }
        attributes[i] = replacedAttributes;
      } else {
        Map<String, Object> oldAttributes = markers[i].getAttributes();
        markers[i].setAttributes(attributes[i]);
        attributes[i] = oldAttributes;
      }
    }
  }

  protected void setMarkerDescriptions(IMarkerSnapshot[] descriptions) {
    markerDescriptions = descriptions;
    // VSCODE: the marker types of Eclipse (bookmarks, tasks, problems) are all in the workspace undo context.
    addContext(WorkspaceUndoUtil.getMarkerUndoContext());
    updateTargetResources();
  }

  private void updateTargetResources() {
    IResource[] newResources = null;
    if (markers == null) {
      if (markerDescriptions != null) {
        newResources = new IResource[markerDescriptions.length];
        for (int i = 0; i < markerDescriptions.length; i++) {
          newResources[i] = markerDescriptions[i].getResource();
        }
      }
    } else {
      newResources = new IResource[markers.length];
      for (int i = 0; i < markers.length; i++) {
        newResources[i] = markers[i].getResource();
      }
    }
    setTargetResources(newResources);
  }

  public IMarker[] getMarkers() {
    return markers;
  }

  protected boolean markersExist() {
    if (markers == null || markers.length == 0) {
      return false;
    }
    for (IMarker marker : markers) {
      if (!marker.exists()) {
        return false;
      }
    }
    return true;
  }

  protected abstract IStatus getBasicUndoStatus();

  protected abstract IStatus getBasicRedoStatus();

  @Override
  public IStatus computeExecutionStatus(IProgressMonitor monitor) {
    IStatus status = getBasicRedoStatus();
    if (status.isOK()) {
      return super.computeExecutionStatus(monitor);
    }
    if (status.getSeverity() == IStatus.ERROR) {
      markInvalid();
    }
    return status;
  }

  @Override
  public IStatus computeUndoableStatus(IProgressMonitor monitor) {
    IStatus status = getBasicUndoStatus();
    if (status.isOK()) {
      return super.computeUndoableStatus(monitor);
    }
    if (status.getSeverity() == IStatus.ERROR) {
      markInvalid();
    }
    return status;
  }

  @Override
  public IStatus computeRedoableStatus(IProgressMonitor monitor) {
    IStatus status = getBasicRedoStatus();
    if (status.isOK()) {
      return super.computeRedoableStatus(monitor);
    }
    if (status.getSeverity() == IStatus.ERROR) {
      markInvalid();
    }
    return status;
  }

  protected IStatus getMarkerDeletionStatus() {
    if (markersExist()) {
      return Status.OK_STATUS;
    }
    return getErrorStatus(UndoMessages.MarkerOperation_MarkerDoesNotExist);
  }

  protected IStatus getMarkerCreationStatus() {
    if (!resourcesExist()) {
      return getErrorStatus(UndoMessages.MarkerOperation_ResourceDoesNotExist);
    } else if (markerDescriptions == null) {
      return getErrorStatus(UndoMessages.MarkerOperation_NotEnoughInfo);
    }
    return Status.OK_STATUS;
  }

  protected IStatus getMarkerUpdateStatus() {
    if (!markersExist()) {
      return getErrorStatus(UndoMessages.MarkerOperation_MarkerDoesNotExist);
    } else if (attributes == null) {
      return getErrorStatus(UndoMessages.MarkerOperation_NotEnoughInfo);
    }
    return Status.OK_STATUS;
  }

  @Override
  protected void appendDescriptiveText(StringBuffer text) {
    super.appendDescriptiveText(text);
    text.append(" markers: ");
    text.append(markers == null ? null : java.util.Arrays.asList(markers));
    text.append('\'');
    text.append(" markerDescriptions: ");
    text.append(markerDescriptions == null ? null : java.util.Arrays.asList(markerDescriptions));
    text.append('\'');
    text.append(" attributes: ");
    text.append(attributes == null ? null : java.util.Arrays.asList(attributes));
    text.append('\'');
  }
}
