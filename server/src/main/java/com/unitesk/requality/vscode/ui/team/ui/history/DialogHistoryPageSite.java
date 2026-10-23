package com.unitesk.requality.vscode.ui.team.ui.history;

import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IPageSite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;

/**
 * {@code org.eclipse.team.internal.ui.history.DialogHistoryPageSite}: the site of a history page shown in a compare
 * editor or a dialog (modal, without a part).
 */
public class DialogHistoryPageSite implements IHistoryPageSite {
  private final Shell shell;
  private ToolBarManager toolBarManager;
  private ISelectionProvider selectionProvider;

  public DialogHistoryPageSite(Shell shell) {
    this.shell = shell;
  }

  @Override
  public Shell getShell() {
    return shell;
  }

  @Override
  public IWorkbenchPart getPart() {
    return null;
  }

  @Override
  public IPageSite getWorkbenchPageSite() {
    return null;
  }

  @Override
  public ToolBarManager getToolBarManager() {
    return toolBarManager;
  }

  public void setToolBarManager(ToolBarManager toolBarManager) {
    this.toolBarManager = toolBarManager;
  }

  @Override
  public void setSelectionProvider(ISelectionProvider provider) {
    this.selectionProvider = provider;
  }

  @Override
  public ISelectionProvider getSelectionProvider() {
    return selectionProvider;
  }

  @Override
  public boolean isModal() {
    return true;
  }
}
