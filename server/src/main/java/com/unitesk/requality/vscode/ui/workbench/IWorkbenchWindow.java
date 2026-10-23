package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.ui.IWorkbenchWindow}: the VS Code window. */
public interface IWorkbenchWindow {
  /** The active dialog shell, or null: dialogs opened with a null parent are shown as VS Code panels. */
  Shell getShell();

  IWorkbenchPage getActivePage();

  IWorkbenchPage[] getPages();

  IWorkbench getWorkbench();

  ISelectionService getSelectionService();

  /** {@code IWorkbenchWindow.addPageListener}. */
  void addPageListener(IPageListener listener);

  void removePageListener(IPageListener listener);

  /** {@code org.eclipse.ui.IWorkbenchWindow.isClosing()}: the VS Code window is never closing while the server runs. */
  boolean isClosing();

  <T> T getService(Class<T> api);
}
