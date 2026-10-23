package com.unitesk.requality.vscode.ui.ide.undo;

import java.io.InputStream;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IContainerSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.ResourceSnapshotFactory;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/** {@code org.eclipse.ui.ide.undo.CreateFileOperation}: creates a file (with its missing folders) with the contents. */
public class CreateFileOperation extends AbstractCreateResourcesOperation {
  public CreateFileOperation(IFile fileHandle, InputStream contents, String label) {
    super(null, label);
    IResourceSnapshot<? extends IResource> resourceDescription;
    if (fileHandle.getParent().exists()) {
      resourceDescription = ResourceSnapshotFactory.fromFileDetails(fileHandle, contents);
    } else {
      IContainerSnapshot<? extends IContainer> containerDescription = ResourceSnapshotFactory
          .fromContainer(fileHandle.getParent());
      IResourceSnapshot<IFile> fileDescription = ResourceSnapshotFactory.fromFileDetails(fileHandle, contents);
      WorkspaceUndoUtil.getFirstLeafFolder(containerDescription).addMember(fileDescription);
      resourceDescription = containerDescription;
    }
    setResourceDescriptions(new IResourceSnapshot<?>[] { resourceDescription });
  }

  @Override
  public IStatus computeExecutionStatus(IProgressMonitor monitor) {
    IStatus status = super.computeExecutionStatus(monitor);
    if (status.isOK()) {
      status = computeCreateStatus(false);
    }
    return status;
  }
}
