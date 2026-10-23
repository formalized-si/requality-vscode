package com.unitesk.requality.vscode.ui.team.ui.history;

import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IPageSite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;

/** {@code org.eclipse.team.ui.history.IHistoryPageSite}: the site of a history page (in the view or in a dialog). */
public interface IHistoryPageSite {
  /** The shell of the page. */
  Shell getShell();

  /** The part (the history view) that shows the page, or null in a dialog. */
  IWorkbenchPart getPart();

  /** The page site of the workbench page (the action bars of the view), or null in a dialog. */
  IPageSite getWorkbenchPageSite();

  /** The toolbar of the page, or null. */
  ToolBarManager getToolBarManager();

  /** Sets the selection provider of the page. */
  void setSelectionProvider(ISelectionProvider provider);

  /** The selection provider of the page, or null. */
  ISelectionProvider getSelectionProvider();

  /** Whether the page is shown in a modal dialog. */
  boolean isModal();
}
