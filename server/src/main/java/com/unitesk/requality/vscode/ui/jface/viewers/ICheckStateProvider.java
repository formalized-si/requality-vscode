package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ICheckStateProvider}. */
public interface ICheckStateProvider {
  boolean isChecked(Object element);

  boolean isGrayed(Object element);
}
