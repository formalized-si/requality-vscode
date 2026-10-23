package com.unitesk.requality.vscode.ui.resources.undo.snapshot;

import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.core.internal.resources.undo.snapshot.FolderSnapshot}: a folder and its members. */
class FolderSnapshot extends ContainerSnapshot<IFolder> {
  FolderSnapshot(IFolder folder) {
    super(folder);
  }

  @Override
  public IFolder createResourceHandle() {
    IPath folderPath = parent.getFullPath().append(name);
    return getWorkspace().getRoot().getFolder(folderPath);
  }

  @Override
  public void createExistentResourceFromHandle(IProgressMonitor mon) throws CoreException {
    IFolder folderHandle = createResourceHandle();
    if (folderHandle.exists()) {
      return;
    }
    folderHandle.create(false, true, mon);
    createChildResources(folderHandle, mon);
  }
}
