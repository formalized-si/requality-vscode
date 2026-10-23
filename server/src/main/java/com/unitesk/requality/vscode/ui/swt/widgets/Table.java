package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;

/**
 * {@code org.eclipse.swt.widgets.Table}. Rows ({@link TableItem}) and columns are widgets of their own. A click on a
 * cell is reported to {@link CellListener}s, which JFace cell editing uses.
 */
public class Table extends Composite {
  /** A cell was activated by the user (click on a selected row, double click or F2). */
  public interface CellListener {
    void cellActivated(TableItem item, int column, String trigger);

    /** The inline editor opened by {@code editCell} was closed (applied or cancelled). */
    default void cellEditDone(TableItem item, int column, JsonElement value, boolean apply) {
    }
  }

  final java.util.List<TableColumn> columns = new ArrayList<>();
  final java.util.List<TableItem> items = new ArrayList<>();
  private final Set<TableItem> selection = new LinkedHashSet<>();
  private final java.util.List<CellListener> cellListeners = new ArrayList<>();
  private boolean headerVisible;
  private boolean linesVisible;
  private TableColumn sortColumn;
  private int sortDirection = SWT.NONE;

  public Table(Composite parent, int style) {
    super(parent, style, "table");
    created();
  }

  @Override
  void syncLayoutData() {
    super.syncLayoutData();
    if (getLayout() instanceof TableAware)
      ((TableAware) getLayout()).apply(this);
  }

  @Override
  java.util.List<Widget> snapshotChildren() {
    java.util.List<Widget> r = new ArrayList<>(columns);
    r.addAll(items);
    r.addAll(super.snapshotChildren());
    return r;
  }

  void addColumn(TableColumn c, int index) {
    if (index < 0 || index > columns.size())
      columns.add(c);
    else
      columns.add(index, c);
  }

  void removeColumn(TableColumn c) {
    columns.remove(c);
  }

  int addItem(TableItem item, int index) {
    if (index < 0 || index > items.size()) {
      items.add(item);
      return items.size() - 1;
    }
    items.add(index, item);
    return index;
  }

  void removeItem(TableItem item) {
    items.remove(item);
    if (selection.remove(item))
      sendSelection();
  }

  private void sendSelection() {
    java.util.List<String> ids = new ArrayList<>();
    for (TableItem i : selection)
      ids.add(i.id);
    set("selection", ids);
  }

  public void setHeaderVisible(boolean show) {
    headerVisible = show;
    set("headerVisible", show);
  }

  public boolean getHeaderVisible() {
    return headerVisible;
  }

  public void setLinesVisible(boolean show) {
    linesVisible = show;
    set("linesVisible", show);
  }

  public boolean getLinesVisible() {
    return linesVisible;
  }

  public int getColumnCount() {
    return columns.size();
  }

  public TableColumn getColumn(int index) {
    return columns.get(index);
  }

  public TableColumn[] getColumns() {
    return columns.toArray(new TableColumn[0]);
  }

  public int indexOf(TableColumn c) {
    return columns.indexOf(c);
  }

  public int getItemCount() {
    return items.size();
  }

  private TableItem lastMouseItem;
  private int lastMouseColumn = -1;
  private int lastMouseX;
  private int lastMouseY;

  @Override
  void mouseEventReceived(Event e) {
    lastMouseItem = e.item instanceof TableItem ? (TableItem) e.item : null;
    lastMouseColumn = e.index;
    lastMouseX = e.x;
    lastMouseY = e.y;
  }

  /** The column under a point of the last mouse event, or -1 (the renderer reports the cell under the pointer). */
  public int getColumnAt(com.unitesk.requality.vscode.ui.swt.graphics.Point point) {
    return point != null && point.x == lastMouseX && point.y == lastMouseY ? lastMouseColumn : -1;
  }

  /**
   * {@code org.eclipse.swt.widgets.Table.getItem(Point)}: the item under a point of the last mouse event (the renderer
   * reports the row under the pointer with each mouse event); other points: null.
   */
  public TableItem getItem(com.unitesk.requality.vscode.ui.swt.graphics.Point point) {
    if (point != null && lastMouseItem != null && !lastMouseItem.isDisposed() && point.x == lastMouseX
        && point.y == lastMouseY)
      return lastMouseItem;
    return null;
  }

  public TableItem getItem(int index) {
    return items.get(index);
  }

  public TableItem[] getItems() {
    return items.toArray(new TableItem[0]);
  }

  public int indexOf(TableItem item) {
    return items.indexOf(item);
  }

  /** Grows or shrinks the item list (VIRTUAL tables). */
  public void setItemCount(int count) {
    while (items.size() > count)
      items.get(items.size() - 1).dispose();
    while (items.size() < count)
      new TableItem(this, SWT.NONE);
  }

  public int getItemHeight() {
    return 20;
  }

  /**
   * {@code org.eclipse.swt.widgets.Table.getHeaderHeight()}: the header row height (0 when the header is hidden). The
   * renderer does not report the header metrics, so one item height is used.
   */
  public int getHeaderHeight() {
    return getHeaderVisible() ? getItemHeight() : 0;
  }

  public void removeAll() {
    for (TableItem i : getItems())
      i.dispose();
    selection.clear();
    sendSelection();
  }

  public void remove(int index) {
    items.get(index).dispose();
  }

  public void remove(int start, int end) {
    for (int i = end; i >= start; i--)
      remove(i);
  }

  public void remove(int[] indices) {
    int[] s = indices.clone();
    Arrays.sort(s);
    for (int i = s.length - 1; i >= 0; i--)
      remove(s[i]);
  }

  public void clear(int index) {
    items.get(index).clear();
  }

  public void clearAll() {
    for (TableItem i : items)
      i.clear();
  }

  public TableItem[] getSelection() {
    return selection.toArray(new TableItem[0]);
  }

  public int getSelectionCount() {
    return selection.size();
  }

  public int getSelectionIndex() {
    return selection.isEmpty() ? -1 : items.indexOf(selection.iterator().next());
  }

  public int[] getSelectionIndices() {
    return selection.stream().mapToInt(items::indexOf).toArray();
  }

  public boolean isSelected(int index) {
    return index >= 0 && index < items.size() && selection.contains(items.get(index));
  }

  public void setSelection(int index) {
    selection.clear();
    select(index);
  }

  public void setSelection(int[] indices) {
    selection.clear();
    for (int i : indices)
      if (i >= 0 && i < items.size())
        selection.add(items.get(i));
    sendSelection();
  }

  public void setSelection(int start, int end) {
    selection.clear();
    for (int i = start; i <= end && i < items.size(); i++)
      selection.add(items.get(i));
    sendSelection();
  }

  public void setSelection(TableItem item) {
    setSelection(item == null ? new TableItem[0] : new TableItem[] { item });
  }

  public void setSelection(TableItem[] sel) {
    selection.clear();
    for (TableItem i : sel)
      if (i != null && !i.isDisposed())
        selection.add(i);
    sendSelection();
  }

  public void select(int index) {
    if (index < 0 || index >= items.size())
      return;
    if (!SWT.has(style, SWT.MULTI))
      selection.clear();
    selection.add(items.get(index));
    sendSelection();
  }

  public void select(int[] indices) {
    for (int i : indices)
      select(i);
  }

  public void selectAll() {
    if (!SWT.has(style, SWT.MULTI))
      return;
    selection.addAll(items);
    sendSelection();
  }

  public void deselect(int index) {
    if (index >= 0 && index < items.size() && selection.remove(items.get(index)))
      sendSelection();
  }

  public void deselectAll() {
    selection.clear();
    sendSelection();
  }

  /** Opens the renderer's inline editor in a cell (used by JFace cell editors). */
  public void editCell(TableItem item, int column, JsonObject editor) {
    JsonObject a = editor.deepCopy();
    a.addProperty("item", item.id);
    a.addProperty("column", column);
    call("editCell", a);
  }

  public void showItem(TableItem item) {
    call("showItem", item.id);
  }

  public void showSelection() {
    if (!selection.isEmpty())
      showItem(selection.iterator().next());
  }

  public void showColumn(TableColumn c) {
  }

  public void setTopIndex(int index) {
    if (index >= 0 && index < items.size())
      showItem(items.get(index));
  }

  public int getTopIndex() {
    return 0;
  }

  public void setSortColumn(TableColumn c) {
    sortColumn = c;
    set("sortColumn", c == null ? null : c.id);
  }

  public TableColumn getSortColumn() {
    return sortColumn;
  }

  public void setSortDirection(int direction) {
    sortDirection = direction;
    set("sortDirection", direction == SWT.UP ? "up" : direction == SWT.DOWN ? "down" : null);
  }

  public int getSortDirection() {
    return sortDirection;
  }

  public void addSelectionListener(SelectionListener l) {
    addTyped(SWT.Selection, l);
    addTyped(SWT.DefaultSelection, l);
  }

  public void removeSelectionListener(SelectionListener l) {
    removeTyped(SWT.Selection, l);
    removeTyped(SWT.DefaultSelection, l);
  }

  public void addCellListener(CellListener l) {
    cellListeners.add(l);
    set("cellEvents", true);
  }

  public void removeCellListener(CellListener l) {
    cellListeners.remove(l);
  }

  @Override
  void releaseChildren() {
    for (TableItem i : getItems())
      i.dispose();
    for (TableColumn c : getColumns())
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
          if (w instanceof TableItem && ((TableItem) w).parent == this)
            selection.add((TableItem) w);
        }
        java.util.List<String> ids = new ArrayList<>();
        for (TableItem i : selection)
          ids.add(i.id);
        setQuiet("selection", ids);
        Event ev = new Event();
        Widget it = d.has("item") && !d.get("item").isJsonNull() ? display.find(d.get("item").getAsString()) : null;
        ev.item = it;
        ev.index = it instanceof TableItem ? items.indexOf(it) : -1;
        notifyListeners("default".equals(type) ? SWT.DefaultSelection : SWT.Selection, ev);
        return;
      }
      case "check": {
        Widget w = display.find(d.get("item").getAsString());
        if (!(w instanceof TableItem))
          return;
        TableItem item = (TableItem) w;
        item.setCheckedQuiet(d.get("checked").getAsBoolean());
        Event ev = new Event();
        ev.item = item;
        ev.index = items.indexOf(item);
        ev.detail = SWT.CHECK;
        notifyListeners(SWT.Selection, ev);
        return;
      }
      case "cellApply":
      case "cellCancel": {
        Widget w = display.find(d.get("item").getAsString());
        if (!(w instanceof TableItem))
          return;
        for (CellListener l : new ArrayList<>(cellListeners))
          l.cellEditDone((TableItem) w, d.get("column").getAsInt(), d.get("value"), "cellApply".equals(type));
        return;
      }
      case "cell": {
        Widget w = display.find(d.get("item").getAsString());
        if (!(w instanceof TableItem))
          return;
        int col = d.get("column").getAsInt();
        String trigger = d.has("trigger") ? d.get("trigger").getAsString() : "click";
        for (CellListener l : new ArrayList<>(cellListeners))
          l.cellActivated((TableItem) w, col, trigger);
        return;
      }
      default:
        super.onClientEvent(type, d);
    }
  }
}
