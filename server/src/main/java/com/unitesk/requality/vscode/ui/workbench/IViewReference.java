package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IViewReference}. */
public interface IViewReference extends IWorkbenchPartReference {
  IViewPart getView(boolean restore);

  default String getSecondaryId() {
    return null;
  }
}
