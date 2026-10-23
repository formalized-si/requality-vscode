package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.ui.IEditorPart}. */
public interface IEditorPart extends IWorkbenchPart {
  IEditorInput getEditorInput();

  IEditorSite getEditorSite();

  void init(IEditorSite site, IEditorInput input) throws PartInitException;

  boolean isDirty();

  void doSave(IProgressMonitor monitor);

  void doSaveAs();

  boolean isSaveAsAllowed();

  default boolean isSaveOnCloseNeeded() {
    return isDirty();
  }
}
