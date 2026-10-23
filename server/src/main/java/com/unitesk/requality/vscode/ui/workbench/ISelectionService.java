package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;

/** {@code org.eclipse.ui.ISelectionService}: the workbench selection (the selection of the active part). */
public interface ISelectionService {
  ISelection getSelection();

  ISelection getSelection(String partId);

  void addSelectionListener(ISelectionListener listener);

  void removeSelectionListener(ISelectionListener listener);

  void addPostSelectionListener(ISelectionListener listener);

  void removePostSelectionListener(ISelectionListener listener);
}
