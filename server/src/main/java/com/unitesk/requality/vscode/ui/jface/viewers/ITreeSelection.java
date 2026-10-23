package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ITreeSelection}. */
public interface ITreeSelection extends IStructuredSelection {
  TreePath[] getPaths();

  TreePath[] getPathsFor(Object element);
}
