package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.ui.part.IPageSite}: the site of a page, provided by the view that hosts it. */
public interface IPageSite {
  /** {@code org.eclipse.ui.part.IPageSite#getShell()}. */
  Shell getShell();

  /** {@code org.eclipse.ui.part.IPageSite#getWorkbenchWindow()}. */
  IWorkbenchWindow getWorkbenchWindow();

  /** {@code org.eclipse.ui.part.IPageSite#getActionBars()}. */
  IActionBars getActionBars();

  /** {@code org.eclipse.ui.part.IPageSite#getSelectionProvider()}. */
  ISelectionProvider getSelectionProvider();

  /** {@code org.eclipse.ui.part.IPageSite#setSelectionProvider(ISelectionProvider)}. */
  void setSelectionProvider(ISelectionProvider provider);
}
