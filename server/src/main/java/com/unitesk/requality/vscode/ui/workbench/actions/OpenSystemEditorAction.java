package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.workbench.IEditorRegistry;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;

/**
 * {@code org.eclipse.ui.actions.OpenSystemEditorAction}: opens the selected files in the system editor (the
 * operating system's application for the file, {@code client/openFile} with the editor "system").
 */
public class OpenSystemEditorAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.OpenSystemEditorAction";

  private final IWorkbenchPage workbenchPage;

  public OpenSystemEditorAction(IWorkbenchPage page) {
    super("&System Editor", () -> page.getWorkbenchWindow().getShell());
    setToolTipText("Edit File with System Editor");
    setId(ID);
    if (page == null)
      throw new IllegalArgumentException();
    this.workbenchPage = page;
  }

  protected IWorkbenchPage getWorkbenchPage() {
    return workbenchPage;
  }

  /** Opens one file (the action's own opening: the system editor). */
  protected void openFile(IFile file) {
    try {
      IDE.openEditor(getWorkbenchPage(), file, IEditorRegistry.SYSTEM_EXTERNAL_EDITOR_ID, true);
    } catch (PartInitException e) {
      MessageDialog.openError(getWorkbenchPage().getWorkbenchWindow().getShell(),
          "Problems Opening System Editor", e.getMessage());
    }
  }

  @Override
  public void run() {
    List<IResource> selected = getSelectedResources();
    for (IResource resource : selected)
      if (resource instanceof IFile)
        openFile((IFile) resource);
  }

  @Override
  protected boolean updateSelection(com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection selection) {
    return super.updateSelection(selection) && selectionIsOfType(IResource.FILE);
  }
}
