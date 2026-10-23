package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.AbstractTreeViewer}. */
public abstract class AbstractTreeViewer extends ColumnViewer {
  public static final int ALL_LEVELS = -1;

  public abstract void expandAll();

  public abstract void collapseAll();

  public abstract void expandToLevel(int level);

  public abstract void expandToLevel(Object element, int level);

  public abstract void setExpandedState(Object element, boolean expanded);

  public abstract boolean getExpandedState(Object element);

  public abstract Object[] getExpandedElements();

  public abstract void setExpandedElements(Object... elements);

  public abstract boolean isExpandable(Object element);

  /** {@code AbstractTreeViewer.add(Object, Object[])}: the children of the parent are read again from the content provider. */
  public void add(Object parentElementOrTreePath, Object[] childElements) {
    refresh(parentElementOrTreePath);
  }

  /** {@code AbstractTreeViewer.remove(Object[])}: the parents of the removed elements are read again. */
  public void remove(Object[] elements) {
    IContentProvider cp = getContentProvider();
    for (Object element : elements) {
      Object parent = cp instanceof ITreeContentProvider ? ((ITreeContentProvider) cp).getParent(element) : null;
      if (parent == null)
        refresh();
      else
        refresh(parent);
    }
  }
}
