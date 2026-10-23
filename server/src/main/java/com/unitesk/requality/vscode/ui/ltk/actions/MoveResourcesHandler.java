package com.unitesk.requality.vscode.ui.ltk.actions;

import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.ltk.ui.RefactoringWizardOpenOperation;
import com.unitesk.requality.vscode.ui.ltk.ui.resource.MoveResourcesWizard;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ltk.internal.ui.refactoring.actions.MoveResourcesHandler}: the handler of the
 * {@code org.eclipse.ltk.ui.refactoring.commands.moveResources} command; it opens the "Move Resources" wizard.
 */
public class MoveResourcesHandler extends AbstractResourcesHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    Shell activeShell = HandlerUtil.getActiveShell(event);
    IStructuredSelection sel = HandlerUtil.getCurrentStructuredSelection(event);
    IResource[] resources = getSelectedResources(sel);
    if (resources.length > 0) {
      MoveResourcesWizard wizard = new MoveResourcesWizard(resources);
      try {
        new RefactoringWizardOpenOperation(wizard).run(DeleteResourcesHandler.shell(activeShell), "Move Resources");
      } catch (InterruptedException e) {
        // canceled by the user
      }
    }
    return null;
  }
}
