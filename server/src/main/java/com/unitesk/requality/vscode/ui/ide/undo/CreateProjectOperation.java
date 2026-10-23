package com.unitesk.requality.vscode.ui.ide.undo;

import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.ResourceSnapshotFactory;

/** {@code org.eclipse.ui.ide.undo.CreateProjectOperation}: creates a project from its description. */
public class CreateProjectOperation extends AbstractCreateResourcesOperation {
  public CreateProjectOperation(IProjectDescription projectDescription, String label) {
    super(new IResourceSnapshot<?>[] { ResourceSnapshotFactory.fromProjectDescription(projectDescription) }, label);
  }
}
