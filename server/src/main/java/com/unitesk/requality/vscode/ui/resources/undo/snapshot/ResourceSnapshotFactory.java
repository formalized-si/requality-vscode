package com.unitesk.requality.vscode.ui.resources.undo.snapshot;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.core.resources.undo.snapshot.ResourceSnapshotFactory}. */
public class ResourceSnapshotFactory {
  private ResourceSnapshotFactory() {
  }

  @SuppressWarnings("unchecked")
  public static <T extends IResource> IResourceSnapshot<T> fromResource(T resource) {
    if (resource.getType() == IResource.PROJECT) {
      return (IResourceSnapshot<T>) new ProjectSnapshot((IProject) resource);
    } else if (resource.getType() == IResource.FOLDER) {
      return (IResourceSnapshot<T>) new FolderSnapshot((IFolder) resource);
    } else if (resource.getType() == IResource.FILE) {
      return (IResourceSnapshot<T>) new FileSnapshot((IFile) resource);
    } else {
      throw new IllegalArgumentException();
    }
  }

  public static IContainerSnapshot<IProject> fromProjectDescription(IProjectDescription projectDescription) {
    return new ProjectSnapshot(projectDescription);
  }

  public static IContainerSnapshot<? extends IContainer> fromContainer(IContainer container) {
    return ContainerSnapshot.fromContainer(container);
  }

  /** A file that does not exist yet (or is replaced) with the given contents. */
  public static IResourceSnapshot<IFile> fromFileDetails(IFile file, InputStream contents) {
    return new FileSnapshot(file, createFileContentDescription(file, contents));
  }

  public static IMarkerSnapshot fromMarker(IMarker marker) throws CoreException {
    return new MarkerSnapshot(marker);
  }

  public static IMarkerSnapshot fromMarkerDetails(String type, Map<String, Object> attributes, IResource resource) {
    return new MarkerSnapshot(type, attributes, resource);
  }

  private static IFileContentSnapshot createFileContentDescription(final IFile file, final InputStream contents) {
    return new IFileContentSnapshot() {
      @Override
      public InputStream getContents() {
        if (contents != null) {
          return contents;
        }
        return new ByteArrayInputStream(new byte[0]);
      }

      @Override
      public String getCharset() {
        try {
          return file.getCharset();
        } catch (CoreException e) {
          return null;
        }
      }

      @Override
      public boolean exists() {
        return true;
      }
    };
  }
}
