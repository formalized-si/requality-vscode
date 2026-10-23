package com.unitesk.requality.vscode.ui.ltk.actions;

import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.ltk.ui.RefactoringWizardOpenOperation;
import com.unitesk.requality.vscode.ui.ltk.ui.resource.DeleteResourcesWizard;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ltk.internal.ui.refactoring.actions.DeleteResourcesHandler}: the handler of the
 * {@code org.eclipse.ltk.ui.refactoring.commands.deleteResources} command; it opens the "Delete Resources" wizard.
 */
public class DeleteResourcesHandler extends AbstractResourcesHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    Shell activeShell = HandlerUtil.getActiveShell(event);
    IStructuredSelection sel = HandlerUtil.getCurrentStructuredSelection(event);
    IResource[] resources = getSelectedResources(sel);
    if (resources.length > 0) {
      DeleteResourcesWizard wizard = new DeleteResourcesWizard(resources);
      try {
        new RefactoringWizardOpenOperation(wizard).run(shell(activeShell), "Delete Resources");
      } catch (InterruptedException e) {
        // canceled by the user
      }
    }
    return null;
  }

  static Shell shell(Shell active) {
    return active != null ? active : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
  }
}
