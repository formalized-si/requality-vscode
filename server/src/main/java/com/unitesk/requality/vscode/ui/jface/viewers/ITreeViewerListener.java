package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ITreeViewerListener}. */
public interface ITreeViewerListener {
  void treeCollapsed(TreeExpansionEvent event);

  void treeExpanded(TreeExpansionEvent event);
}
