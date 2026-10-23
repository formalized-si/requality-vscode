package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;

/** {@code org.eclipse.ui.views.properties.IPropertySheetPage}: a page of the property sheet. */
public interface IPropertySheetPage extends IPage {
  /** {@code org.eclipse.ui.views.properties.IPropertySheetPage#selectionChanged(IWorkbenchPart, ISelection)}. */
  void selectionChanged(IWorkbenchPart part, ISelection selection);
}
