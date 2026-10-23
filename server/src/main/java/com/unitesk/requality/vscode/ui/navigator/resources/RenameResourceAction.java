package com.unitesk.requality.vscode.ui.navigator.resources;

import java.io.File;
import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.ide.actions.LTKLauncher;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.dialogs.IInputValidator;
import com.unitesk.requality.vscode.ui.jface.dialogs.InputDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.ide.undo.MoveResourcesOperation;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.actions.RenameResourceAction}: asks for the new name ("Rename Resource"); a project is renamed
 * by the rename refactoring with the rename participants of plugin.xml ({@link RenameProjectRefactoring}), a file or
 * folder is moved to the new name in its folder.
 */
public class RenameResourceAction extends Action {
  public static final String ID = "org.eclipse.ui.RenameResourceAction";
  private final Supplier<Shell> shell;
  private IProject project;
  private IStructuredSelection selection;

  public RenameResourceAction(Supplier<Shell> shell) {
    super("Rename...");
    setId(ID);
    setToolTipText("Rename the selected resource");
    this.shell = shell;
  }

  private com.unitesk.requality.vscode.ui.resources.IResource resource;

  public void selectionChanged(IStructuredSelection selection) {
    this.selection = selection;
    Object first = selection != null && selection.size() == 1 ? selection.getFirstElement() : null;
    project = first instanceof IProject ? (IProject) first : null;
    resource = first instanceof com.unitesk.requality.vscode.ui.resources.IResource
        && ((com.unitesk.requality.vscode.ui.resources.IResource) first).getType() != com.unitesk.requality.vscode.ui.resources.IResource.ROOT
            ? (com.unitesk.requality.vscode.ui.resources.IResource) first : null;
    setEnabled(resource != null);
  }

  /** A file or folder: the new name is asked for and the resource is moved to it (a rename in its folder). */
  private void renameResource(com.unitesk.requality.vscode.ui.resources.IResource r) {
    com.unitesk.requality.vscode.ui.resources.IContainer parent = r.getParent();
    IInputValidator validator = newText -> {
      if (newText == null || newText.trim().isEmpty())
        return "Enter a new name";
      if (newText.equals(r.getName()))
        return "'" + newText + "' is the current name";
      com.unitesk.requality.vscode.ui.runtime.IStatus st = ResourcesPlugin.getWorkspace().validateName(newText, r.getType());
      if (!st.isOK())
        return st.getMessage();
      if (parent.findMember(newText) != null)
        return "'" + newText + "' already exists.";
      return null;
    };
    InputDialog d = new InputDialog(shell.get(), "Rename Resource", "New name:", r.getName(), validator);
    if (d.open() != Window.OK)
      return;
    try {
      WorkspaceUndoUtil.execute(new MoveResourcesOperation(r, parent.getFullPath().append(d.getValue()),
          "Rename Resource"), new NullProgressMonitor(), shell.get());
    } catch (com.unitesk.requality.vscode.ui.runtime.CoreException e) {
      com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog.openError(shell.get(), "Rename Problems",
          "Problems occurred renaming the selected resource.", e.getStatus());
    }
  }

  /** The name of the project as Eclipse shows it: the name of its description (.project). */
  static String projectName(IProject p) {
    String n = com.unitesk.requality.tool.CoreUtils.getProjectName(p.getLocation().toFile());
    return n == null ? p.getName() : n;
  }

  @Override
  public void run() {
    // Eclipse: the rename wizard of the LTK command; the dialogs below are its fallback when the command is not available
    if (LTKLauncher.openRenameWizard(selection))
      return;
    IProject p = project;
    if (p == null) {
      if (resource != null)
        renameResource(resource);
      return;
    }
    String current = projectName(p);
    IInputValidator validator = newText -> {
      if (newText == null || newText.trim().isEmpty())
        return "Enter a new name";
      if (newText.equals(current))
        return "'" + newText + "' is the current name";
      if (newText.matches(".*[\\\\/:*?\"<>|].*"))
        return "'" + newText + "' is not a valid project name";
      for (IProject other : ResourcesPlugin.getWorkspace().getRoot().getProjects())
        if (other != null && !other.equals(p) && new File(other.getLocation().toFile(), ".project").isFile()
            && newText.equals(projectName(other)))
          return "A project with that name already exists";
      return null;
    };
    InputDialog d = new InputDialog(shell.get(), "Rename Resource", "New name:", current, validator);
    if (d.open() != Window.OK)
      return;
    RenameProjectRefactoring.schedule(p, current, d.getValue());
  }
}
