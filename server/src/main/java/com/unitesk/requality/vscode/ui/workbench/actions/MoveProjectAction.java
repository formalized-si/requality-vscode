package com.unitesk.requality.vscode.ui.workbench.actions;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.ide.dialogs.ProjectLocationMoveDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.ide.undo.MoveProjectOperation;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.actions.MoveResourceAction} for one project ({@code MoveProjectAction}): asks for the new
 * location ({@link ProjectLocationMoveDialog}) and moves the project there.
 */
public class MoveProjectAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.MoveResourceAction";

  public MoveProjectAction(Supplier<Shell> shell) {
    super("Mo&ve...", shell);
    setToolTipText("Move");
    setId(ID);
    setActionDefinitionId(ActionFactory.MOVE.getCommandId());
  }

  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    List<IProject> projects = getSelectedProjects();
    return selection.size() == 1 && projects.size() == 1 && projects.get(0).isOpen();
  }

  @Override
  public void run() {
    List<IProject> projects = getSelectedProjects();
    if (projects.size() != 1)
      return;
    IProject project = projects.get(0);
    ProjectLocationMoveDialog dialog = new ProjectLocationMoveDialog(getShell(), project);
    dialog.setTitle("Move Project");
    if (dialog.open() != Window.OK || dialog.getResult() == null)
      return;
    URI newLocation = (URI) dialog.getResult()[0];
    WorkspaceOperations.run(getShell(), "Moving project", "Move Problems",
        "Problems encountered while moving resources.", monitor -> WorkspaceUndoUtil.execute(
            new MoveProjectOperation(project, newLocation, "Move Project"), monitor, getShell()));
  }
}
