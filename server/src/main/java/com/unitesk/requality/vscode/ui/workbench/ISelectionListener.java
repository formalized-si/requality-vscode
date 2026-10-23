package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;

/** {@code org.eclipse.ui.ISelectionListener}. */
@FunctionalInterface
public interface ISelectionListener {
  void selectionChanged(IWorkbenchPart part, ISelection selection);
}
