package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IViewPart}. */
public interface IViewPart extends IWorkbenchPart {
  IViewSite getViewSite();

  void init(IViewSite site) throws PartInitException;
}
