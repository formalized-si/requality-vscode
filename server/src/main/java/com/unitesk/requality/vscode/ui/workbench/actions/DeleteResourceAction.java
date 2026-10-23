package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.List;
import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.ide.actions.LTKLauncher;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.ltk.ui.RefactoringWizardOpenOperation;
import com.unitesk.requality.vscode.ui.ltk.ui.resource.DeleteResourcesWizard;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.actions.DeleteResourceAction}: deletes the selected resources. The "Delete Resources" wizard of
 * the LTK command is opened first ({@link LTKLauncher}); the projects are removed from the workspace, with their contents
 * on disk when the user asks for it.
 */
public class DeleteResourceAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.DeleteResourceAction";

  public DeleteResourceAction(Supplier<Shell> shell) {
    super("&Delete", shell);
    setToolTipText("Delete");
    setId(ID);
    setActionDefinitionId(ActionFactory.DELETE.getCommandId());
  }

  /** Only projects or only non-projects, all existing. */
  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    if (!getSelectedNonResources().isEmpty())
      return false;
    List<IResource> resources = getSelectedResources();
    if (resources.isEmpty())
      return false;
    if (!(selectionIsOfType(IResource.PROJECT) || selectionIsOfType(IResource.FILE | IResource.FOLDER)))
      return false;
    for (IResource r : resources)
      if (!r.exists())
        return false;
    return true;
  }

  @Override
  public void run() {
    List<IResource> resources = getSelectedResources();
    if (resources.isEmpty())
      return;
    if (LTKLauncher.openDeleteWizard(getStructuredSelection()))
      return;
    // the wizard of the command, opened directly when the command is not available
    try {
      new RefactoringWizardOpenOperation(new DeleteResourcesWizard(resources.toArray(new IResource[0])))
          .run(getShell(), "Delete Resources");
    } catch (InterruptedException e) {
      // canceled by the user
    }
  }
}
