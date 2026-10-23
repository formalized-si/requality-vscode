package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.ui.part.PageSite}: a page site that takes its shell, window and action bars from a part site. */
public class PageSite implements IPageSite {
  private final IWorkbenchPartSite partSite;
  private ISelectionProvider selectionProvider;

  public PageSite(IWorkbenchPartSite partSite) {
    this.partSite = partSite;
  }

  @Override
  public Shell getShell() {
    return partSite.getShell();
  }

  @Override
  public IWorkbenchWindow getWorkbenchWindow() {
    return partSite.getWorkbenchWindow();
  }

  @Override
  public IActionBars getActionBars() {
    return partSite.getActionBars();
  }

  @Override
  public ISelectionProvider getSelectionProvider() {
    return selectionProvider;
  }

  @Override
  public void setSelectionProvider(ISelectionProvider provider) {
    selectionProvider = provider;
  }
}
