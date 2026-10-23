package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ICellModifier}. */
public interface ICellModifier {
  boolean canModify(Object element, String property);

  Object getValue(Object element, String property);

  void modify(Object element, String property, Object value);
}
