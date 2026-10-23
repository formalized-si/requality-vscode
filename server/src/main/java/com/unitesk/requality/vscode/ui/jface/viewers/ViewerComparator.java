package com.unitesk.requality.vscode.ui.jface.viewers;

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;

/** {@code org.eclipse.jface.viewers.ViewerComparator}: by category, then label text. */
public class ViewerComparator {
  private final Comparator<Object> comparator;

  public ViewerComparator() {
    this(null);
  }

  @SuppressWarnings("unchecked")
  public ViewerComparator(Comparator<?> comparator) {
    this.comparator = comparator != null ? (Comparator<Object>) comparator : (Comparator<Object>) (Comparator<?>) Collator.getInstance();
  }

  public int category(Object element) {
    return 0;
  }

  protected Comparator<Object> getComparator() {
    return comparator;
  }

  public int compare(Viewer viewer, Object e1, Object e2) {
    int c1 = category(e1), c2 = category(e2);
    if (c1 != c2)
      return c1 - c2;
    String n1 = label(viewer, e1), n2 = label(viewer, e2);
    return comparator.compare(n1, n2);
  }

  static String label(Viewer viewer, Object e) {
    if (viewer instanceof StructuredViewer) {
      IBaseLabelProvider lp = ((StructuredViewer) viewer).getLabelProvider();
      if (lp instanceof ILabelProvider) {
        String t = ((ILabelProvider) lp).getText(e);
        return t == null ? "" : t;
      }
    }
    return e == null ? "" : e.toString();
  }

  public boolean isSorterProperty(Object element, String property) {
    return false;
  }

  public void sort(Viewer viewer, Object[] elements) {
    Arrays.sort(elements, (a, b) -> compare(viewer, a, b));
  }
}
