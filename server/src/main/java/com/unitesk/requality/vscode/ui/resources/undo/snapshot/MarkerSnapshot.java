package com.unitesk.requality.vscode.ui.resources.undo.snapshot;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.core.internal.resources.undo.snapshot.MarkerSnapshot}: the type and attributes of a marker. */
class MarkerSnapshot implements IMarkerSnapshot {
  final IResource resource;
  private final String type;
  private final Map<String, Object> attributes;

  MarkerSnapshot(IMarker marker) throws CoreException {
    this.resource = marker.getResource();
    this.type = marker.getType();
    this.attributes = new HashMap<>(marker.getAttributes());
  }

  MarkerSnapshot(String type, Map<String, Object> attributes, IResource resource) {
    this.resource = resource;
    this.type = type;
    this.attributes = attributes == null ? new HashMap<>() : new HashMap<>(attributes);
  }

  @Override
  public IMarker createMarker() throws CoreException {
    IMarker marker = resource.createMarker(type);
    marker.setAttributes(attributes);
    return marker;
  }

  @Override
  public IResource getResource() {
    return resource;
  }

  @Override
  public String getType() {
    return type;
  }
}
