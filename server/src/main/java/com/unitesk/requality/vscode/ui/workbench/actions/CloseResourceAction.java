package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.ui.actions.CloseResourceAction} ("Close Project"): closes the selected open projects. */
public class CloseResourceAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.CloseResourceAction";

  public CloseResourceAction(Supplier<Shell> shell) {
    this("Clo&se Project", shell);
    setActionDefinitionId(ActionFactory.CLOSE_PROJECT.getCommandId());
  }

  protected CloseResourceAction(String text, Supplier<Shell> shell) {
    super(text, shell);
    setToolTipText("Close Project");
    setId(ID);
  }

  /** Enabled when the selection is projects and one of them is open. */
  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    if (!selectionIsOfType(IResource.PROJECT))
      return false;
    for (IProject p : getSelectedProjects())
      if (p.isOpen())
        return true;
    return false;
  }

  /** The projects to close. */
  protected List<IProject> getProjectsToClose() {
    List<IProject> r = new ArrayList<>();
    for (IProject p : getSelectedProjects())
      if (p.isOpen())
        r.add(p);
    return r;
  }

  @Override
  public void run() {
    List<IProject> projects = getProjectsToClose();
    if (projects.isEmpty())
      return;
    // IDE.saveAllEditors: the editors of the projects' resources are saved (with a prompt) before closing.
    if (!com.unitesk.requality.vscode.ui.ide.IDE.saveAllEditors(projects.toArray(new IResource[0]), true))
      return;
    WorkspaceOperations.run(getShell(), "Closing", "Problems Closing Project",
        "Problems encountered while closing the project.", monitor -> {
          for (IProject p : projects)
            p.close(monitor);
        });
  }
}
