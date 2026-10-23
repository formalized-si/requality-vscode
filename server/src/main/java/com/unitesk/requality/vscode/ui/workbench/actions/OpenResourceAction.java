package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.actions.OpenResourceAction} ("Open Project"): opens the selected closed projects, and the
 * closed projects they reference when the user agrees.
 */
public class OpenResourceAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.OpenResourceAction";

  public OpenResourceAction(Supplier<Shell> shell) {
    super("&Open Project", shell);
    setToolTipText("Open Project");
    setId(ID);
    setActionDefinitionId(ActionFactory.OPEN_PROJECT.getCommandId());
  }

  /** Enabled when the selection is projects and one of them is closed. */
  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    if (!selectionIsOfType(com.unitesk.requality.vscode.ui.resources.IResource.PROJECT))
      return false;
    for (IProject p : getSelectedProjects())
      if (!p.isOpen())
        return true;
    return false;
  }

  @Override
  public void run() {
    List<IProject> toOpen = new ArrayList<>();
    for (IProject p : getSelectedProjects())
      if (!p.isOpen())
        toOpen.add(p);
    List<IProject> referenced = new ArrayList<>();
    WorkspaceOperations.run(getShell(), "Opening", "Problems Opening Project",
        "Problems occurred opening the selected resources.", monitor -> {
          for (IProject p : toOpen) {
            p.open(monitor);
            try {
              for (IProject r : p.getReferencedProjects())
                if (r.exists() && !r.isOpen() && !referenced.contains(r) && !toOpen.contains(r))
                  referenced.add(r);
            } catch (CoreException e) {
              // no references
            }
          }
        });
    if (!referenced.isEmpty() && MessageDialog.openQuestion(getShell(), "Open Referenced Projects",
        "Should referenced projects also be opened?"))
      WorkspaceOperations.run(getShell(), "Opening", "Problems Opening Project",
          "Problems occurred opening the selected resources.", monitor -> {
            for (IProject r : referenced)
              r.open(monitor);
          });
  }
}
