package com.unitesk.requality.vscode.ui.navigator.resources;

import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.navigator.ICommonMenuConstants;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory;
import com.unitesk.requality.vscode.ui.workbench.actions.MoveProjectAction;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.RefactorActionProvider}: Move... and Rename... of the
 * selected resources in group.reorganize, and the global move/rename handlers of the view. (The resources of the
 * Requality Explorer are projects: Move... is the MoveProjectAction, Rename... the RenameResourceAction of a project.)
 */
public class RefactorActionProvider extends CommonActionProvider {
  private MoveProjectAction moveProjectAction;
  private com.unitesk.requality.vscode.ui.workbench.actions.MoveResourceAction moveResourceAction;
  private RenameResourceAction renameAction;

  @Override
  public void init(ICommonActionExtensionSite anActionSite) {
    super.init(anActionSite);
    Supplier<Shell> shell = () -> anActionSite.getViewSite() == null ? null : anActionSite.getViewSite().getShell();
    moveProjectAction = new MoveProjectAction(shell);
    moveResourceAction = new com.unitesk.requality.vscode.ui.workbench.actions.MoveResourceAction(shell);
    renameAction = new RenameResourceAction(shell);
  }

  /** MoveResourceAction: a project is moved by MoveProjectAction, files and folders by MoveResourceAction. */
  private com.unitesk.requality.vscode.ui.jface.action.IAction moveAction(IStructuredSelection selection) {
    return !selection.isEmpty() && selection.getFirstElement() instanceof IProject ? moveProjectAction : moveResourceAction;
  }

  private IStructuredSelection selection() {
    ISelection s = getContext() == null ? null : getContext().getSelection();
    return s instanceof IStructuredSelection ? (IStructuredSelection) s : StructuredSelection.EMPTY;
  }

  /** ResourceSelectionUtil.allResourcesAreOfType(selection, PROJECT | FOLDER | FILE). */
  private static boolean allResources(IStructuredSelection sel) {
    if (sel.isEmpty())
      return false;
    for (Object o : sel.toList())
      if (!(o instanceof com.unitesk.requality.vscode.ui.resources.IResource)
          || ((com.unitesk.requality.vscode.ui.resources.IResource) o).getType() == com.unitesk.requality.vscode.ui.resources.IResource.ROOT)
        return false;
    return true;
  }

  @Override
  public void fillContextMenu(IMenuManager menu) {
    IStructuredSelection selection = selection();
    if (!allResources(selection))
      return;
    moveProjectAction.selectionChanged(selection);
    moveResourceAction.selectionChanged(selection);
    com.unitesk.requality.vscode.ui.jface.action.IAction move = moveAction(selection);
    menu.appendToGroup(ICommonMenuConstants.GROUP_REORGANIZE, move);
    renameAction.selectionChanged(selection);
    menu.insertAfter(move.getId(), renameAction);
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    updateActionBars();
    actionBars.setGlobalActionHandler(ActionFactory.MOVE.getId(), moveAction(selection()));
    actionBars.setGlobalActionHandler(ActionFactory.RENAME.getId(), renameAction);
  }

  @Override
  public void updateActionBars() {
    if (getContext() == null)
      return; // the actions keep the selection they were last given
    IStructuredSelection selection = selection();
    moveProjectAction.selectionChanged(selection);
    moveResourceAction.selectionChanged(selection);
    renameAction.selectionChanged(selection);
  }
}
