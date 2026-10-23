package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.ui.IWorkbenchPartSite} (with IViewSite and IEditorSite). */
public interface IWorkbenchPartSite {
  String getId();

  IWorkbenchPart getPart();

  IWorkbenchPage getPage();

  IWorkbenchWindow getWorkbenchWindow();

  Shell getShell();

  ISelectionProvider getSelectionProvider();

  void setSelectionProvider(ISelectionProvider provider);

  <T> T getService(Class<T> api);

  IActionBars getActionBars();

  /** Context menus of server-hosted parts are rendered by the remote widgets; nothing to register. */
  default void registerContextMenu(com.unitesk.requality.vscode.ui.jface.action.MenuManager menuManager,
      ISelectionProvider selectionProvider) {
  }

  default void registerContextMenu(String menuId, com.unitesk.requality.vscode.ui.jface.action.MenuManager menuManager,
      ISelectionProvider selectionProvider) {
  }

  default String getPluginId() {
    return "com.unitesk.requality";
  }

  default String getRegisteredName() {
    return getId();
  }
}
