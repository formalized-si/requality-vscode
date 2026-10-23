package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.TreeExpansionEvent}. */
public class TreeExpansionEvent {
  private final AbstractTreeViewer viewer;
  private final Object element;

  public TreeExpansionEvent(AbstractTreeViewer viewer, Object element) {
    this.viewer = viewer;
    this.element = element;
  }

  public Object getElement() {
    return element;
  }

  public AbstractTreeViewer getTreeViewer() {
    return viewer;
  }
}
