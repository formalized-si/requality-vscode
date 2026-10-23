package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IEditorReference}. */
public interface IEditorReference extends IWorkbenchPartReference {
  IEditorPart getEditor(boolean restore);

  IEditorInput getEditorInput() throws PartInitException;

  boolean isDirty();
}
