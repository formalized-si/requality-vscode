package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ITreeContentProvider}. */
public interface ITreeContentProvider extends IStructuredContentProvider {
  Object[] getChildren(Object parentElement);

  Object getParent(Object element);

  boolean hasChildren(Object element);
}
