package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.IContentProvider}. */
public interface IContentProvider {
  default void dispose() {
  }

  default void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
  }
}
