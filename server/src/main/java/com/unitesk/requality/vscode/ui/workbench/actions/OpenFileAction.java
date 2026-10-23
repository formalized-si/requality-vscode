package com.unitesk.requality.vscode.ui.workbench.actions;

import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.workbench.IEditorRegistry.IEditorDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;

/**
 * {@code org.eclipse.ui.actions.OpenFileAction}: opens the selected files ("Open"): in the editor given, else in the
 * editor the file is remembered for ({@link IDE#openEditor(IWorkbenchPage, IFile, boolean)}).
 */
public class OpenFileAction extends OpenSystemEditorAction {
  public static final String ID = "org.eclipse.ui.OpenFileAction";

  private final IEditorDescriptor editorDescriptor;

  public OpenFileAction(IWorkbenchPage page) {
    this(page, null);
  }

  public OpenFileAction(IWorkbenchPage page, IEditorDescriptor descriptor) {
    super(page);
    setText(descriptor == null ? "&Open" : descriptor.getLabel());
    setToolTipText("Edit File");
    setId(ID);
    this.editorDescriptor = descriptor;
  }

  @Override
  protected void openFile(IFile file) {
    try {
      boolean activate = true;
      if (editorDescriptor == null)
        IDE.openEditor(getWorkbenchPage(), file, activate);
      else
        IDE.openEditor(getWorkbenchPage(), file, editorDescriptor.getId(), activate);
    } catch (PartInitException e) {
      MessageDialog.openError(getWorkbenchPage().getWorkbenchWindow().getShell(), "Problems Opening Editor",
          e.getMessage());
    }
  }
}
