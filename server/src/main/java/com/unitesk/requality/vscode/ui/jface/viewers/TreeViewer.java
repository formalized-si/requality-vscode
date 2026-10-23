package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Item;
import com.unitesk.requality.vscode.ui.swt.widgets.Tree;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeItem;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;

/**
 * {@code org.eclipse.jface.viewers.TreeViewer}. Children are created lazily when an item is expanded, like JFace.
 */
public class TreeViewer extends AbstractTreeViewer {
  private static final String MATERIALIZED = "requality.materialized";
  private final Tree tree;
  private final List<ITreeViewerListener> treeListeners = new CopyOnWriteArrayList<>();
  private int autoExpandLevel;

  public TreeViewer(Composite parent) {
    this(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
  }

  public TreeViewer(Composite parent, int style) {
    this(new Tree(parent, style));
  }

  public TreeViewer(Tree tree) {
    this.tree = tree;
    tree.addListener(SWT.Selection, e -> {
      if (e.detail == SWT.CHECK)
        handleCheck(e.item);
      else
        fireSelectionChanged(new SelectionChangedEvent(this, getSelection()));
    });
    tree.addListener(SWT.DefaultSelection, e -> fireDoubleClick(new DoubleClickEvent(this, getSelection())));
    tree.addListener(SWT.Expand, e -> {
      if (e.item instanceof TreeItem) {
        materialize((TreeItem) e.item);
        TreeExpansionEvent te = new TreeExpansionEvent(this, e.item.getData());
        for (ITreeViewerListener l : treeListeners)
          l.treeExpanded(te);
      }
    });
    tree.addListener(SWT.Collapse, e -> {
      if (e.item instanceof TreeItem) {
        TreeExpansionEvent te = new TreeExpansionEvent(this, e.item.getData());
        for (ITreeViewerListener l : treeListeners)
          l.treeCollapsed(te);
      }
    });
  }

  protected void handleCheck(Widget item) {
  }

  public Tree getTree() {
    return tree;
  }

  @Override
  public Control getControl() {
    return tree;
  }

  @Override
  protected Object[] columnWidgets() {
    return tree.getColumns();
  }

  @Override
  void enableCellEvents() {
  }

  private ITreeContentProvider cp() {
    return getContentProvider() instanceof ITreeContentProvider ? (ITreeContentProvider) getContentProvider() : null;
  }

  private Object[] childrenOf(Object parent, boolean root) {
    if (getContentProvider() == null)
      return new Object[0];
    Object[] raw;
    if (root)
      raw = ((IStructuredContentProvider) getContentProvider()).getElements(parent);
    else if (cp() != null)
      raw = cp().getChildren(parent);
    else
      raw = new Object[0];
    return getFilteredChildren(parent, raw);
  }

  private boolean hasKids(Object element) {
    ITreeContentProvider p = cp();
    if (p == null)
      return false;
    if (getFilters().length == 0)
      return p.hasChildren(element);
    return p.hasChildren(element) && childrenOf(element, false).length > 0;
  }

  private void fill(Widget parent, Object parentElement, boolean root) {
    for (Object child : childrenOf(parentElement, root))
      newItem(parent, child);
  }

  private TreeItem newItem(Widget parent, Object child) {
    TreeItem item = parent instanceof Tree ? new TreeItem((Tree) parent, SWT.NONE) : new TreeItem((TreeItem) parent, SWT.NONE);
    item.setData(child);
    item.setData(MATERIALIZED, Boolean.FALSE);
    updateItem(item, child);
    item.setHasChildren(hasKids(child));
    return item;
  }

  /**
   * Updates the items of a parent to its current children (AbstractTreeViewer.updateChildren): an item keeps its place
   * and its widget; the item of an equal element keeps its children, which are updated too, the item of another element
   * gets the new element, collapsed and without children. Items are added or disposed at the end.
   */
  private void updateChildren(Widget parent, Object parentElement, boolean root) {
    Object[] children = childrenOf(parentElement, root);
    TreeItem[] items = parent instanceof Tree ? ((Tree) parent).getItems() : ((TreeItem) parent).getItems();
    int min = Math.min(items.length, children.length);
    for (int i = 0; i < min; i++) {
      TreeItem item = items[i];
      Object child = children[i];
      boolean same = same(item.getData(), child);
      item.setData(child);
      if (!same) {
        item.setData(MATERIALIZED, Boolean.FALSE);
        item.removeAll();
        item.setExpanded(false);
      }
      updateItem(item, child);
      boolean kids = hasKids(child);
      item.setHasChildren(kids);
      if (!kids) {
        item.removeAll();
        item.setData(MATERIALIZED, Boolean.FALSE);
        item.setExpanded(false);
      } else if (Boolean.TRUE.equals(item.getData(MATERIALIZED)))
        updateChildren(item, child, false);
    }
    for (int i = items.length - 1; i >= min; i--)
      items[i].dispose();
    for (int i = min; i < children.length; i++)
      newItem(parent, children[i]);
  }

  private void materialize(TreeItem item) {
    if (Boolean.TRUE.equals(item.getData(MATERIALIZED)))
      return;
    item.setData(MATERIALIZED, Boolean.TRUE);
    item.removeAll();
    fill(item, item.getData(), false);
  }

  private void collectExpanded(TreeItem[] items, List<Object> out) {
    for (TreeItem i : items) {
      if (i.getExpanded()) {
        out.add(i.getData());
        collectExpanded(i.getItems(), out);
      }
    }
  }

  @Override
  public void refresh() {
    if (tree.isDisposed())
      return;
    Object[] expanded = getExpandedElements();
    List<Object> selected = new ArrayList<>(getStructuredSelection().toList());
    if (getInput() != null)
      updateChildren(tree, getInput(), true);
    else
      tree.removeAll();
    if (autoExpandLevel != 0)
      expandToLevel(autoExpandLevel);
    setExpandedElements(expanded);
    selectElements(selected, false);
  }

  @Override
  public void refresh(Object element) {
    if (element == null || element == getInput()) {
      refresh();
      return;
    }
    for (TreeItem item : itemsFor(element)) {
      if (item.isDisposed())
        continue;
      Object[] expanded = getExpandedElements();
      item.setData(element);
      updateItem(item, element);
      boolean kids = hasKids(element);
      item.setHasChildren(kids);
      if (!kids) {
        item.removeAll();
        item.setData(MATERIALIZED, Boolean.FALSE);
        item.setExpanded(false);
      } else if (Boolean.TRUE.equals(item.getData(MATERIALIZED))) {
        updateChildren(item, element, false);
        setExpandedElements(expanded);
      }
    }
  }

  @Override
  public void update(Object element, String[] properties) {
    for (TreeItem i : itemsFor(element))
      updateItem(i, element);
  }

  public void add(Object parentElement, Object childElement) {
    refresh(parentElement);
  }

  public void add(Object parentElement, Object[] childElements) {
    refresh(parentElement);
  }

  public void remove(Object element) {
    for (TreeItem i : itemsFor(element))
      i.dispose();
  }

  public void remove(Object[] elements) {
    for (Object e : elements)
      remove(e);
  }

  public void remove(Object parent, Object[] elements) {
    remove(elements);
  }

  private List<TreeItem> itemsFor(Object element) {
    List<TreeItem> r = new ArrayList<>();
    collect(tree.getItems(), element, r);
    return r;
  }

  private static void collect(TreeItem[] items, Object element, List<TreeItem> out) {
    for (TreeItem i : items) {
      if (same(i.getData(), element))
        out.add(i);
      collect(i.getItems(), element, out);
    }
  }

  @Override
  protected Item findItem(Object element) {
    List<TreeItem> l = itemsFor(element);
    return l.isEmpty() ? null : l.get(0);
  }

  /** Materializes the path to an element using the content provider's parents. */
  private TreeItem internalExpand(Object element) {
    List<TreeItem> found = itemsFor(element);
    if (!found.isEmpty())
      return found.get(0);
    ITreeContentProvider p = cp();
    if (p == null)
      return null;
    List<Object> path = new ArrayList<>();
    Set<Object> seen = new HashSet<>();
    for (Object e = p.getParent(element); e != null && e != getInput() && seen.add(e); e = p.getParent(e))
      path.add(e);
    Collections.reverse(path);
    for (Object ancestor : path) {
      List<TreeItem> items = itemsFor(ancestor);
      if (items.isEmpty())
        return null;
      TreeItem it = items.get(0);
      materialize(it);
      it.setExpanded(true);
    }
    found = itemsFor(element);
    return found.isEmpty() ? null : found.get(0);
  }

  private void selectElements(List<Object> elements, boolean reveal) {
    List<TreeItem> items = new ArrayList<>();
    for (Object o : elements) {
      TreeItem it = reveal ? internalExpand(o) : (itemsFor(o).isEmpty() ? null : itemsFor(o).get(0));
      if (it != null)
        items.add(it);
    }
    tree.setSelection(items.toArray(new TreeItem[0]));
    if (reveal && !items.isEmpty())
      tree.showItem(items.get(0));
  }

  @Override
  public ISelection getSelection() {
    if (tree.isDisposed())
      return TreeSelection.EMPTY;
    List<TreePath> paths = new ArrayList<>();
    for (TreeItem i : tree.getSelection()) {
      List<Object> segs = new ArrayList<>();
      for (TreeItem x = i; x != null; x = x.getParentItem())
        segs.add(0, x.getData());
      paths.add(new TreePath(segs.toArray()));
    }
    return new TreeSelection(paths.toArray(new TreePath[0]));
  }

  @Override
  public void setSelection(ISelection selection, boolean reveal) {
    List<Object> l = new ArrayList<>();
    if (selection instanceof IStructuredSelection)
      l.addAll(((IStructuredSelection) selection).toList());
    selectElements(l, true);
    fireSelectionChanged(new SelectionChangedEvent(this, getSelection()));
  }

  @Override
  public void reveal(Object element) {
    TreeItem it = internalExpand(element);
    if (it != null)
      tree.showItem(it);
  }

  @Override
  public void expandAll() {
    expandToLevel(ALL_LEVELS);
  }

  @Override
  public void collapseAll() {
    for (TreeItem i : tree.getItems())
      collapse(i);
  }

  private void collapse(TreeItem i) {
    i.setExpanded(false);
    for (TreeItem c : i.getItems())
      collapse(c);
  }

  @Override
  public void expandToLevel(int level) {
    for (TreeItem i : tree.getItems())
      expand(i, level, 1);
  }

  private void expand(TreeItem i, int level, int depth) {
    if (level != ALL_LEVELS && depth > level)
      return;
    if (!hasKids(i.getData()))
      return;
    materialize(i);
    i.setExpanded(true);
    for (TreeItem c : i.getItems())
      expand(c, level, depth + 1);
  }

  @Override
  public void expandToLevel(Object element, int level) {
    TreeItem it = internalExpand(element);
    if (it != null)
      expand(it, level, 1);
  }

  public void collapseToLevel(Object element, int level) {
    for (TreeItem i : itemsFor(element))
      collapse(i);
  }

  @Override
  public void setExpandedState(Object element, boolean expanded) {
    if (expanded) {
      TreeItem it = internalExpand(element);
      if (it != null && hasKids(element)) {
        materialize(it);
        it.setExpanded(true);
      }
    } else {
      for (TreeItem i : itemsFor(element))
        i.setExpanded(false);
    }
  }

  @Override
  public boolean getExpandedState(Object element) {
    for (TreeItem i : itemsFor(element))
      if (i.getExpanded())
        return true;
    return false;
  }

  @Override
  public Object[] getExpandedElements() {
    List<Object> r = new ArrayList<>();
    collectExpanded(tree.getItems(), r);
    return r.toArray();
  }

  public Object[] getVisibleExpandedElements() {
    return getExpandedElements();
  }

  @Override
  public void setExpandedElements(Object... elements) {
    for (Object e : new LinkedHashSet<>(Arrays.asList(elements)))
      setExpandedState(e, true);
  }

  public boolean isExpandable(Object element) {
    return hasKids(element);
  }

  public void setAutoExpandLevel(int level) {
    autoExpandLevel = level;
  }

  public int getAutoExpandLevel() {
    return autoExpandLevel;
  }

  public void addTreeListener(ITreeViewerListener l) {
    treeListeners.add(l);
  }

  public void removeTreeListener(ITreeViewerListener l) {
    treeListeners.remove(l);
  }
}
