package com.unitesk.requality.vscode.ui.ide.model;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.workbench.IElementFactory;
import com.unitesk.requality.vscode.ui.workbench.IMemento;

/**
 * {@code org.eclipse.ui.internal.ide.model.ResourceFactory}: re-creates a persisted resource (a working set element) from its
 * memento: the full path and the type of the resource in the workspace.
 */
public class ResourceFactory implements IElementFactory {
  /** The id of the factory, as the element factory of the IDE. */
  public static final String ID = "org.eclipse.ui.internal.model.ResourceFactory";

  @Override
  public IAdaptable createElement(IMemento memento) {
    String path = memento.getString("path");
    Integer type = memento.getInteger("type");
    if (path == null || type == null) {
      return null;
    }
    IPath fullPath = Path.fromOSString(path);
    IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
    switch (type) {
      case IResource.PROJECT:
        IProject project = root.getProject(fullPath.segment(0));
        return project;
      case IResource.FOLDER:
        IFolder folder = root.getFolder(fullPath);
        return folder;
      case IResource.FILE:
        IFile file = root.getFile(fullPath);
        return file;
      default:
        return null;
    }
  }
}
