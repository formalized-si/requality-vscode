package com.unitesk.requality.vscode.ui.resources.undo.snapshot;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.core.internal.resources.undo.snapshot.ContainerSnapshot}: a folder or project and its members. */
abstract class ContainerSnapshot<T extends IContainer> extends AbstractResourceSnapshot<T>
    implements IContainerSnapshot<T> {
  String name;
  final List<IResourceSnapshot<? extends IResource>> members = new ArrayList<>();

  /** The snapshot of a container, or of its first missing ancestor (the folders to create before it). */
  @SuppressWarnings("unchecked")
  static <R extends IContainer> ContainerSnapshot<? extends R> fromContainer(R container) {
    IPath fullPath = container.getFullPath();
    ContainerSnapshot<? extends IContainer> firstCreatedParent = null;
    ContainerSnapshot<? extends IContainer> currentContainerDescription = null;
    IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
    IContainer currentContainer = (IContainer) root.findMember(fullPath);
    if (currentContainer != null) {
      return (ContainerSnapshot<R>) ResourceSnapshotFactory.fromResource(container);
    }
    currentContainer = root;
    for (int i = 0; i < fullPath.segmentCount(); i++) {
      String currentSegment = fullPath.segment(i);
      IResource resource = currentContainer.findMember(currentSegment);
      if (resource != null) {
        currentContainer = (IContainer) resource;
      } else if (i == 0) {
        firstCreatedParent = new ProjectSnapshot(root.getProject(currentSegment));
        currentContainerDescription = firstCreatedParent;
      } else {
        IFolder folderHandle = currentContainer.getFolder(Path.fromOSString(currentSegment));
        FolderSnapshot currentFolder = new FolderSnapshot(folderHandle);
        currentContainer = folderHandle;
        if (currentContainerDescription != null) {
          currentContainerDescription.addMember(currentFolder);
        }
        currentContainerDescription = currentFolder;
        if (firstCreatedParent == null) {
          firstCreatedParent = currentFolder;
        }
      }
    }
    return (ContainerSnapshot<? extends R>) firstCreatedParent;
  }

  ContainerSnapshot() {
  }

  ContainerSnapshot(T container) {
    super(container);
    this.name = container.getName();
    if (container.isAccessible()) {
      try {
        for (IResource resourceMember : container.members()) {
          members.add(ResourceSnapshotFactory.fromResource(resourceMember));
        }
      } catch (CoreException e) {
        // the members are not kept
      }
    }
  }

  final void createChildResources(IContainer parentHandle, IProgressMonitor monitor) throws CoreException {
    for (IResourceSnapshot<? extends IResource> member : members) {
      if (member instanceof AbstractResourceSnapshot) {
        ((AbstractResourceSnapshot<? extends IResource>) member).parent = parentHandle;
      }
      member.createResource(monitor);
    }
  }

  @Override
  public void recordStateFromHistory(IProgressMonitor mon) throws CoreException {
    for (IResourceSnapshot<? extends IResource> member : members) {
      if (member instanceof FileSnapshot fileSnapshot) {
        fileSnapshot.recordStateFromHistory(mon);
      } else if (member instanceof FolderSnapshot folderSnapshot) {
        folderSnapshot.recordStateFromHistory(mon);
      }
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public IResourceSnapshot<? extends IResource>[] getMembers() {
    return members.toArray(new IResourceSnapshot<?>[members.size()]);
  }

  @Override
  public void addMember(IResourceSnapshot<? extends IResource> member) {
    members.add(member);
  }

  @Override
  public boolean verifyExistence(boolean checkMembers) {
    if (!super.verifyExistence(checkMembers)) {
      return false;
    }
    if (checkMembers) {
      for (IResourceSnapshot<? extends IResource> member : members) {
        if (!member.verifyExistence(checkMembers)) {
          return false;
        }
      }
    }
    return true;
  }
}
