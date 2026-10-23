package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.events.TreeListener;

/** {@code org.eclipse.swt.widgets.Tree}. Expanding an item fires Expand before the renderer shows its children. */
public class Tree extends Composite {
  final java.util.List<TreeItem> items = new ArrayList<>();
  final java.util.List<TreeColumn> columns = new ArrayList<>();
  private final Set<TreeItem> selection = new LinkedHashSet<>();
  private boolean headerVisible;
  private boolean linesVisible;

  public Tree(Composite parent, int style) {
    super(parent, style, "tree");
    created();
  }

  @Override
  java.util.List<Widget> snapshotChildren() {
    java.util.List<Widget> r = new ArrayList<>(columns);
    r.addAll(items);
    r.addAll(super.snapshotChildren());
    return r;
  }

  void addColumn(TreeColumn c, int index) {
    if (index < 0 || index > columns.size())
      columns.add(c);
    else
      columns.add(index, c);
  }

  void removeColumn(TreeColumn c) {
    columns.remove(c);
  }

  void unselect(TreeItem item) {
    if (selection.remove(item))
      sendSelection();
  }

  private void sendSelection() {
    java.util.List<String> ids = new ArrayList<>();
    for (TreeItem i : selection)
      ids.add(i.id);
    set("selection", ids);
  }

  public void setHeaderVisible(boolean b) {
    headerVisible = b;
    set("headerVisible", b);
  }

  private TreeColumn sortColumn;
  private int sortDirection = SWT.NONE;

  /** {@code Tree.setSortColumn}: the column whose header shows the sort indicator. */
  public void setSortColumn(TreeColumn c) {
    sortColumn = c;
    set("sortColumn", c == null ? null : c.id);
  }

  public TreeColumn getSortColumn() {
    return sortColumn;
  }

  /** {@code Tree.setSortDirection}: SWT.UP, SWT.DOWN or SWT.NONE. */
  public void setSortDirection(int direction) {
    sortDirection = direction;
    set("sortDirection", direction == SWT.UP ? "up" : direction == SWT.DOWN ? "down" : null);
  }

  public int getSortDirection() {
    return sortDirection;
  }

  public boolean getHeaderVisible() {
    return headerVisible;
  }

  public void setLinesVisible(boolean b) {
    linesVisible = b;
  }

  public boolean getLinesVisible() {
    return linesVisible;
  }

  public int getColumnCount() {
    return columns.size();
  }

  public TreeColumn getColumn(int i) {
    return columns.get(i);
  }

  public TreeColumn[] getColumns() {
    return columns.toArray(new TreeColumn[0]);
  }

  public int getItemCount() {
    return items.size();
  }

  private TreeItem lastMouseItem;
  private int lastMouseX;
  private int lastMouseY;

  @Override
  void mouseEventReceived(Event e) {
    lastMouseItem = e.item instanceof TreeItem ? (TreeItem) e.item : null;
    lastMouseX = e.x;
    lastMouseY = e.y;
  }

  public TreeItem getItem(int index) {
    return items.get(index);
  }

  /**
   * {@code org.eclipse.swt.widgets.Tree.getItem(Point)}: the item under a point of the last mouse event (the renderer
   * reports the row under the pointer with each mouse event); other points: null.
   */
  public TreeItem getItem(com.unitesk.requality.vscode.ui.swt.graphics.Point point) {
    if (point != null && lastMouseItem != null && !lastMouseItem.isDisposed() && point.x == lastMouseX
        && point.y == lastMouseY)
      return lastMouseItem;
    return null;
  }

  public TreeItem[] getItems() {
    return items.toArray(new TreeItem[0]);
  }

  public int indexOf(TreeItem item) {
    return items.indexOf(item);
  }

  public void setItemCount(int count) {
    while (items.size() > count)
      items.get(items.size() - 1).dispose();
    while (items.size() < count)
      new TreeItem(this, SWT.NONE);
  }

  public int getItemHeight() {
    return 20;
  }

  public void removeAll() {
    for (TreeItem i : getItems())
      i.dispose();
    selection.clear();
    sendSelection();
  }

  public void clearAll(boolean all) {
  }

  public TreeItem[] getSelection() {
    return selection.toArray(new TreeItem[0]);
  }

  public int getSelectionCount() {
    return selection.size();
  }

  public void setSelection(TreeItem item) {
    setSelection(item == null ? new TreeItem[0] : new TreeItem[] { item });
  }

  public void setSelection(TreeItem[] sel) {
    selection.clear();
    for (TreeItem i : sel)
      if (i != null && !i.isDisposed()) {
        selection.add(i);
        for (TreeItem p = i.getParentItem(); p != null; p = p.getParentItem())
          p.setExpanded(true);
      }
    sendSelection();
  }

  public void select(TreeItem item) {
    if (!SWT.has(style, SWT.MULTI))
      selection.clear();
    selection.add(item);
    sendSelection();
  }

  public void deselect(TreeItem item) {
    unselect(item);
  }

  public void deselectAll() {
    selection.clear();
    sendSelection();
  }

  public void selectAll() {
    if (!SWT.has(style, SWT.MULTI))
      return;
    collect(items, selection);
    sendSelection();
  }

  private static void collect(java.util.List<TreeItem> list, Set<TreeItem> out) {
    for (TreeItem i : list) {
      out.add(i);
      collect(i.items, out);
    }
  }

  public void showItem(TreeItem item) {
    for (TreeItem p = item.getParentItem(); p != null; p = p.getParentItem())
      p.setExpanded(true);
    call("showItem", item.id);
  }

  public void showSelection() {
    if (!selection.isEmpty())
      showItem(selection.iterator().next());
  }

  public TreeItem getTopItem() {
    return items.isEmpty() ? null : items.get(0);
  }

  public void setTopItem(TreeItem item) {
    showItem(item);
  }

  public void addSelectionListener(SelectionListener l) {
    addTyped(SWT.Selection, l);
    addTyped(SWT.DefaultSelection, l);
  }

  public void removeSelectionListener(SelectionListener l) {
    removeTyped(SWT.Selection, l);
    removeTyped(SWT.DefaultSelection, l);
  }

  public void addTreeListener(TreeListener l) {
    addTyped(SWT.Expand, l);
    addTyped(SWT.Collapse, l);
  }

  public void removeTreeListener(TreeListener l) {
    removeTyped(SWT.Expand, l);
    removeTyped(SWT.Collapse, l);
  }

  @Override
  void releaseChildren() {
    for (TreeItem i : getItems())
      i.dispose();
    for (TreeColumn c : getColumns())
      c.dispose();
    super.releaseChildren();
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    switch (type) {
      case "selection":
      case "default": {
        selection.clear();
        for (JsonElement e : d.getAsJsonArray("selection")) {
          Widget w = display.find(e.getAsString());
          if (w instanceof TreeItem && ((TreeItem) w).tree == this)
            selection.add((TreeItem) w);
        }
        java.util.List<String> ids = new ArrayList<>();
        for (TreeItem i : selection)
          ids.add(i.id);
        setQuiet("selection", ids);
        Event ev = new Event();
        ev.item = d.has("item") && !d.get("item").isJsonNull() ? display.find(d.get("item").getAsString()) : null;
        notifyListeners("default".equals(type) ? SWT.DefaultSelection : SWT.Selection, ev);
        return;
      }
      case "expand":
      case "collapse": {
        Widget w = display.find(d.get("item").getAsString());
        if (!(w instanceof TreeItem))
          return;
        TreeItem item = (TreeItem) w;
        boolean expand = "expand".equals(type);
        Event ev = new Event();
        ev.item = item;
        notifyListeners(expand ? SWT.Expand : SWT.Collapse, ev);
        item.setExpanded(expand);
        return;
      }
      case "cellclick": {
        // a click on a cell of a tree with columns (MouseDown on the item; index: the column of the cell)
        Widget w = display.find(d.get("item").getAsString());
        if (!(w instanceof TreeItem))
          return;
        Event ev = new Event();
        ev.item = w;
        ev.index = d.has("column") ? d.get("column").getAsInt() : 0;
        notifyListeners(SWT.MouseDown, ev);
        return;
      }
      case "check": {
        Widget w = display.find(d.get("item").getAsString());
        if (!(w instanceof TreeItem))
          return;
        TreeItem item = (TreeItem) w;
        item.setCheckedQuiet(d.get("checked").getAsBoolean());
        Event ev = new Event();
        ev.item = item;
        ev.detail = SWT.CHECK;
        notifyListeners(SWT.Selection, ev);
        return;
      }
      default:
        super.onClientEvent(type, d);
    }
  }
}
