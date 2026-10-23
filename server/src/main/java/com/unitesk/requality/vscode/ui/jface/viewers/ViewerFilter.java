package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.List;

/** {@code org.eclipse.jface.viewers.ViewerFilter}. */
public abstract class ViewerFilter {
  public abstract boolean select(Viewer viewer, Object parentElement, Object element);

  public Object[] filter(Viewer viewer, Object parent, Object[] elements) {
    List<Object> r = new ArrayList<>();
    for (Object e : elements)
      if (select(viewer, parent, e))
        r.add(e);
    return r.toArray();
  }

  public boolean isFilterProperty(Object element, String property) {
    return false;
  }
}
