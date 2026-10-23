package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.jface.viewers.StructuredViewer}: filtering, sorting, selection and double clicks. */
public abstract class StructuredViewer extends ContentViewer {
  private final List<ViewerFilter> filters = new ArrayList<>();
  private ViewerComparator comparator;
  private final List<IDoubleClickListener> doubleClickListeners = new CopyOnWriteArrayList<>();
  private final List<ISelectionChangedListener> postSelectionListeners = new CopyOnWriteArrayList<>();
  private final List<IOpenListener> openListeners = new CopyOnWriteArrayList<>();
  private boolean useHashlookup;

  /**
   * {@code org.eclipse.jface.viewers.StructuredViewer#isBusy()}: whether the viewer is in the middle of an update. The
   * VS Code tree is updated synchronously, so the viewer is never busy between calls.
   */
  // VSCODE: updates run synchronously on the UI thread, so no busy state is kept.
  public boolean isBusy() {
    return false;
  }

  public void addFilter(ViewerFilter f) {
    filters.add(f);
    refresh();
  }

  public void removeFilter(ViewerFilter f) {
    filters.remove(f);
    refresh();
  }

  public void setFilters(ViewerFilter... fs) {
    filters.clear();
    filters.addAll(Arrays.asList(fs));
    refresh();
  }

  public ViewerFilter[] getFilters() {
    return filters.toArray(new ViewerFilter[0]);
  }

  public void resetFilters() {
    filters.clear();
    refresh();
  }

  public void setComparator(ViewerComparator c) {
    comparator = c;
    refresh();
  }

  public ViewerComparator getComparator() {
    return comparator;
  }

  public void setSorter(ViewerSorter s) {
    setComparator(s);
  }

  public ViewerSorter getSorter() {
    return comparator instanceof ViewerSorter ? (ViewerSorter) comparator : null;
  }

  public void setUseHashlookup(boolean b) {
    useHashlookup = b;
  }

  public void setComparer(Object comparer) {
  }

  /** Filtered and sorted children of {@code parent}. */
  protected Object[] getFilteredChildren(Object parent, Object[] raw) {
    Object[] r = raw == null ? new Object[0] : raw.clone();
    for (ViewerFilter f : filters)
      r = f.filter(this, parent, r);
    if (comparator != null)
      comparator.sort(this, r);
    return r;
  }

  protected Object[] getSortedChildren(Object parent, Object[] raw) {
    return getFilteredChildren(parent, raw);
  }

  public void addDoubleClickListener(IDoubleClickListener l) {
    doubleClickListeners.add(l);
  }

  public void removeDoubleClickListener(IDoubleClickListener l) {
    doubleClickListeners.remove(l);
  }

  protected void fireDoubleClick(DoubleClickEvent e) {
    for (IDoubleClickListener l : doubleClickListeners)
      l.doubleClick(e);
    OpenEvent oe = new OpenEvent(this, e.getSelection());
    for (IOpenListener l : openListeners)
      l.open(oe);
  }

  /** {@code StructuredViewer.handleOpen(SelectionEvent)}: opens the selection (the open listeners are notified). */
  protected void handleOpen(Object event) {
    fireOpen(new OpenEvent(this, getSelection()));
  }

  /** {@code StructuredViewer.fireOpen(OpenEvent)}. */
  protected void fireOpen(OpenEvent event) {
    for (IOpenListener l : openListeners)
      l.open(event);
  }

  public void addOpenListener(IOpenListener l) {
    openListeners.add(l);
  }

  public void removeOpenListener(IOpenListener l) {
    openListeners.remove(l);
  }

  public void addPostSelectionChangedListener(ISelectionChangedListener l) {
    postSelectionListeners.add(l);
  }

  public void removePostSelectionChangedListener(ISelectionChangedListener l) {
    postSelectionListeners.remove(l);
  }

  @Override
  protected void fireSelectionChanged(SelectionChangedEvent event) {
    super.fireSelectionChanged(event);
    for (ISelectionChangedListener l : postSelectionListeners)
      l.selectionChanged(event);
  }

  public IStructuredSelection getStructuredSelection() {
    ISelection s = getSelection();
    return s instanceof IStructuredSelection ? (IStructuredSelection) s : StructuredSelection.EMPTY;
  }

  public abstract void refresh(Object element);

  public void refresh(boolean updateLabels) {
    refresh();
  }

  public void refresh(Object element, boolean updateLabels) {
    refresh(element);
  }

  public abstract void update(Object element, String[] properties);

  public void update(Object[] elements, String[] properties) {
    for (Object e : elements)
      update(e, properties);
  }

  public abstract void reveal(Object element);

  public Object[] getRawChildren(Object parent) {
    IContentProvider cp = getContentProvider();
    if (cp instanceof IStructuredContentProvider)
      return ((IStructuredContentProvider) cp).getElements(parent);
    return new Object[0];
  }

  // ---- label helpers shared by the item-based viewers

  protected String text(Object element, int column) {
    IBaseLabelProvider lp = getLabelProvider();
    if (lp instanceof ITableLabelProvider) {
      String t = ((ITableLabelProvider) lp).getColumnText(element, column);
      return t == null ? "" : t;
    }
    if (lp instanceof ILabelProvider) {
      String t = ((ILabelProvider) lp).getText(element);
      return t == null ? "" : t;
    }
    return element == null ? "" : element.toString();
  }

  protected Image image(Object element, int column) {
    IBaseLabelProvider lp = getLabelProvider();
    if (lp instanceof ITableLabelProvider)
      return ((ITableLabelProvider) lp).getColumnImage(element, column);
    if (lp instanceof ILabelProvider)
      return ((ILabelProvider) lp).getImage(element);
    return null;
  }

  protected Color foreground(Object element, int column) {
    IBaseLabelProvider lp = getLabelProvider();
    if (lp instanceof ITableColorProvider)
      return ((ITableColorProvider) lp).getForeground(element, column);
    if (lp instanceof IColorProvider)
      return ((IColorProvider) lp).getForeground(element);
    return null;
  }

  protected Color background(Object element, int column) {
    IBaseLabelProvider lp = getLabelProvider();
    if (lp instanceof ITableColorProvider)
      return ((ITableColorProvider) lp).getBackground(element, column);
    if (lp instanceof IColorProvider)
      return ((IColorProvider) lp).getBackground(element);
    return null;
  }

  protected Font font(Object element) {
    IBaseLabelProvider lp = getLabelProvider();
    return lp instanceof IFontProvider ? ((IFontProvider) lp).getFont(element) : null;
  }

  protected static boolean same(Object a, Object b) {
    return a == null ? b == null : a.equals(b);
  }
}
