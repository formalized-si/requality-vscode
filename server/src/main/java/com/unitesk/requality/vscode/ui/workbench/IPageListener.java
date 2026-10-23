package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IPageListener}: the listener of the pages of a window. */
public interface IPageListener {
  void pageActivated(IWorkbenchPage page);

  void pageClosed(IWorkbenchPage page);

  void pageOpened(IWorkbenchPage page);
}
