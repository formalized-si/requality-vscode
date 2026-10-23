package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.List;
import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.ide.actions.LTKLauncher;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.dialogs.ContainerSelectionDialog;

/**
 * {@code org.eclipse.ui.actions.MoveResourceAction} for files and folders: the destination is chosen in a "Move
 * Resources" container selection, then the resources are moved ({@code MoveFilesAndFoldersOperation}).
 */
public class MoveResourceAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.MoveResourceAction";

  public MoveResourceAction(Supplier<Shell> shell) {
    super("Mo&ve...", shell);
    setToolTipText("Move");
    setId(ID);
    setActionDefinitionId(ActionFactory.MOVE.getCommandId());
  }

  /** Files and folders with a common parent. */
  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    if (!selectionIsOfType(IResource.FILE | IResource.FOLDER) || getSelectedResources().isEmpty())
      return false;
    Object parent = getSelectedResources().get(0).getParent();
    for (IResource r : getSelectedResources())
      if (r.getParent() == null || !r.getParent().equals(parent))
        return false;
    return true;
  }

  @Override
  public void run() {
    if (LTKLauncher.openMoveWizard(getStructuredSelection()))
      return;
    // the destination dialog below is the fallback when the LTK command is not available
    List<IResource> resources = getSelectedResources();
    String msg = resources.size() == 1 ? "Choose destination for '" + resources.get(0).getName() + "':"
        : "Choose destination for " + resources.size() + " selected resources:";
    ContainerSelectionDialog dialog = new ContainerSelectionDialog(getShell(),
        ResourcesPlugin.getWorkspace().getRoot(), false, msg);
    dialog.setTitle("Move Resources");
    if (dialog.open() != Window.OK || dialog.getResult() == null || dialog.getResult().length == 0)
      return;
    IPath path = (IPath) dialog.getResult()[0];
    IResource target = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
    if (!(target instanceof IContainer))
      return;
    CopyFilesAndFoldersOperation.move(getShell()).copyResources(resources.toArray(new IResource[0]), (IContainer) target);
  }
}
