package com.unitesk.requality.vscode.ui.ltk.actions;

import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.ltk.ui.RefactoringWizardOpenOperation;
import com.unitesk.requality.vscode.ui.ltk.ui.resource.RenameResourceWizard;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ltk.internal.ui.refactoring.actions.RenameResourceHandler}: the handler of the
 * {@code org.eclipse.ltk.ui.refactoring.commands.renameResource} command; it opens the "Rename Resource" wizard for the
 * single selected resource.
 */
public class RenameResourceHandler extends AbstractResourcesHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    Shell activeShell = HandlerUtil.getActiveShell(event);
    IStructuredSelection sel = HandlerUtil.getCurrentStructuredSelection(event);
    IResource[] resources = getSelectedResources(sel);
    if (resources.length == 1) {
      // VSCODE: the in-place rename of the tree editor (no wizard) is not ported; the wizard is always opened
      RenameResourceWizard wizard = new RenameResourceWizard(resources[0]);
      try {
        new RefactoringWizardOpenOperation(wizard).run(DeleteResourcesHandler.shell(activeShell), "Rename Resource");
      } catch (InterruptedException e) {
        // canceled by the user
      }
    }
    return null;
  }
}
