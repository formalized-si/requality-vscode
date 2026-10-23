package com.unitesk.requality.vscode.ui.ide.undo;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IContainerSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.ResourceSnapshotFactory;

/** {@code org.eclipse.ui.ide.undo.CreateFolderOperation}: creates a folder (with its missing parent folders). */
public class CreateFolderOperation extends AbstractCreateResourcesOperation {
  public CreateFolderOperation(IFolder folderHandle, String label) {
    super(null, label);
    IContainerSnapshot<? extends IContainer> containerDescription = ResourceSnapshotFactory.fromContainer(folderHandle);
    setResourceDescriptions(new IResourceSnapshot<?>[] { containerDescription });
  }
}
