package com.unitesk.requality.vscode.ui.ide.wizards;

import com.google.gson.JsonArray;
import com.unitesk.requality.vscode.services.ExplorerService;
import com.unitesk.requality.vscode.services.SelectionService;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.workbench.INewWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;

/**
 * {@code org.eclipse.ui.wizards.newresource.BasicNewResourceWizard}: the abstract base of the standard wizards that
 * create new resources in the workspace. Records the workbench and the selection, and selects and reveals the new
 * resource.
 */
public abstract class BasicNewResourceWizard extends Wizard implements INewWizard {
  private IWorkbench workbench;

  /** The current selection. */
  protected IStructuredSelection selection;

  /** Creates an empty wizard for creating a new resource in the workspace. */
  protected BasicNewResourceWizard() {
    super();
  }

  /** The selection which was passed to {@code init}. */
  public IStructuredSelection getSelection() {
    return selection;
  }

  /** The workbench which was passed to {@code init}. */
  public IWorkbench getWorkbench() {
    return workbench;
  }

  /** Records the given workbench and selection, and initializes the default page image. */
  @Override
  public void init(IWorkbench theWorkbench, IStructuredSelection currentSelection) {
    this.workbench = theWorkbench;
    this.selection = currentSelection;
    initializeDefaultPageImageDescriptor();
  }

  /** The default implementation uses the generic new wizard image (none in VS Code). */
  protected void initializeDefaultPageImageDescriptor() {
  }

  /** Selects and reveals the newly added resource in the explorer of the active window. */
  protected void selectAndReveal(IResource newResource) {
    selectAndReveal(newResource, getWorkbench().getActiveWorkbenchWindow());
  }

  /**
   * {@code BasicNewResourceWizard.selectAndReveal(IResource, IWorkbenchWindow)}: the resource is selected in the
   * explorer (VS Code: the Requality Explorer is the only view that shows the workspace resources).
   */
  public static void selectAndReveal(IResource resource, IWorkbenchWindow window) {
    if (window == null || resource == null || window.getActivePage() == null)
      return;
    String id = resource instanceof IProject ? ExplorerService.PROJECT_PREFIX + resource.getName()
        : ExplorerService.RESOURCE_PREFIX + resource.getLocation().toOSString();
    JsonArray ids = new JsonArray();
    ids.add(id);
    SelectionService.select("explorer", ids);
  }
}
