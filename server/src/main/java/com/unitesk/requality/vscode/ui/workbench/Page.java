package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/** {@code org.eclipse.ui.part.Page}: the base of pages hosted by a page book view. */
public abstract class Page implements IPage {
  private IPageSite site;
  private IActionBars actionBars;

  /** {@code org.eclipse.ui.part.Page#init(IPageSite)}. */
  @Override
  public void init(IPageSite pageSite) throws PartInitException {
    site = pageSite;
  }

  /** {@code org.eclipse.ui.part.Page#getSite()}. */
  @Override
  public IPageSite getSite() {
    return site;
  }

  /** {@code org.eclipse.ui.part.Page#setActionBars(IActionBars)}. */
  @Override
  public void setActionBars(IActionBars actionBars) {
    this.actionBars = actionBars;
  }

  @Override
  public abstract void createControl(Composite parent);

  @Override
  public void dispose() {
  }
}
