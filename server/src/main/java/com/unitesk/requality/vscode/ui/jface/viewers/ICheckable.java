package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ICheckable}. */
public interface ICheckable {
  void addCheckStateListener(ICheckStateListener listener);

  boolean getChecked(Object element);

  void removeCheckStateListener(ICheckStateListener listener);

  boolean setChecked(Object element, boolean state);
}
