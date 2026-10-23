package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/**
 * {@code org.eclipse.ui.part.IPage}: a page of a page book view (for example the page of a
 * {@link PropertySheet}). The subset of the Eclipse interface used by the ported code.
 */
public interface IPage {
  /** {@code org.eclipse.ui.part.IPage#init(IPageSite)}. */
  void init(IPageSite pageSite) throws PartInitException;

  /** {@code org.eclipse.ui.part.IPage#getSite()}. */
  IPageSite getSite();

  /** {@code org.eclipse.ui.part.IPage#setActionBars(IActionBars)}. */
  void setActionBars(IActionBars actionBars);

  /** {@code org.eclipse.ui.part.IPage#createControl(Composite)}. */
  void createControl(Composite parent);

  /** {@code org.eclipse.ui.part.IPage#getControl()}. */
  Control getControl();

  /** {@code org.eclipse.ui.part.IPage#setFocus()}. */
  void setFocus();

  /** {@code org.eclipse.ui.part.IPage#dispose()}. */
  void dispose();
}
