package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IViewSite}. */
public interface IViewSite extends IWorkbenchPartSite {
  default String getSecondaryId() {
    return null;
  }
}
