package com.unitesk.requality.vscode.ui.tabbed;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;

/** {@code org.eclipse.ui.views.properties.tabbed.ISection}. */
public interface ISection {
  void createControls(Composite parent, TabbedPropertySheetPage page);

  void setInput(IWorkbenchPart part, ISelection selection);

  void aboutToBeShown();

  void aboutToBeHidden();

  void dispose();

  int getMinimumHeight();

  boolean shouldUseExtraSpace();

  void refresh();
}
