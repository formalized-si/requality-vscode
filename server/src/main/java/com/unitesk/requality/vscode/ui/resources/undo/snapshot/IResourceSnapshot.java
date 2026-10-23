package com.unitesk.requality.vscode.ui.resources.undo.snapshot;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.core.resources.undo.snapshot.IResourceSnapshot}: the state of a resource (its location, contents,
 * members and markers) recorded so that the resource can be recreated by an undo.
 */
public interface IResourceSnapshot<T extends IResource> {
  T createResourceHandle();

  String getName();

  T createResource(IProgressMonitor monitor) throws CoreException;

  void createExistentResourceFromHandle(IProgressMonitor monitor) throws CoreException;

  boolean isValid();

  void recordStateFromHistory(IProgressMonitor monitor) throws CoreException;

  boolean verifyExistence(boolean checkMembers);
}
