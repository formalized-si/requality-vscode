package com.unitesk.requality.vscode.ui.workbench.actions;

import java.net.URI;

import com.unitesk.requality.vscode.ui.ide.dialogs.ProjectLocationSelectionDialog;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.actions.CopyProjectOperation}: asks for the name and location of the copy
 * ({@link ProjectLocationSelectionDialog}) and copies the project there.
 */
public class CopyProjectOperation {
  private final Shell parentShell;

  public CopyProjectOperation(Shell shell) {
    this.parentShell = shell;
  }

  /** Copies the project; false when the user canceled or the copy failed. */
  public boolean copyProject(IProject project) {
    ProjectLocationSelectionDialog dialog = new ProjectLocationSelectionDialog(parentShell, project);
    dialog.setTitle("Copy Project");
    if (dialog.open() != Window.OK)
      return false;
    Object[] destinationPaths = dialog.getResult();
    if (destinationPaths == null)
      return false;
    String newName = (String) destinationPaths[0];
    URI newLocation = destinationPaths[1] == null ? null : (URI) destinationPaths[1];
    return WorkspaceOperations.run(parentShell, "Copying project", "Copy Problems",
        "Problems encountered while copying resources.", monitor -> WorkspaceUndoUtil.executeWithoutHistory(
            // as Eclipse (bug 219901): the undoable operation is executed directly, not through the operation
            // history, so a project copy is not undone by Edit > Undo
            new com.unitesk.requality.vscode.ui.ide.undo.CopyProjectOperation(project, newName, newLocation,
                "Copy Project"),
            monitor, parentShell));
  }
}
