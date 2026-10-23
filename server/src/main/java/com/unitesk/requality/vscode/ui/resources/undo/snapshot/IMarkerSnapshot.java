package com.unitesk.requality.vscode.ui.resources.undo.snapshot;

import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.core.resources.undo.snapshot.IMarkerSnapshot}: the type and attributes of a marker. */
public interface IMarkerSnapshot {
  IMarker createMarker() throws CoreException;

  IResource getResource();

  String getType();
}
