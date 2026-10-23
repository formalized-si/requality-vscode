package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ISelectionProvider}. */
public interface ISelectionProvider {
  void addSelectionChangedListener(ISelectionChangedListener listener);

  ISelection getSelection();

  void removeSelectionChangedListener(ISelectionChangedListener listener);

  void setSelection(ISelection selection);
}
