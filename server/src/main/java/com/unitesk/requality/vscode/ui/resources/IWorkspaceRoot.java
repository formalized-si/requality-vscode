package com.unitesk.requality.vscode.ui.resources;

import java.net.URI;

import com.unitesk.requality.vscode.ui.runtime.IPath;

/** {@code org.eclipse.core.resources.IWorkspaceRoot}. */
public interface IWorkspaceRoot extends IContainer {
  IProject[] getProjects();

  IProject[] getProjects(int memberFlags);

  IProject getProject(String name);

  IFile getFileForLocation(IPath location);

  IContainer getContainerForLocation(IPath location);

  IFile[] findFilesForLocationURI(URI location);

  IContainer[] findContainersForLocationURI(URI location);
}
