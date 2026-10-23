package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Point;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Item;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableColumn;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;

/** {@code org.eclipse.jface.viewers.TableViewer}. */
public class TableViewer extends ColumnViewer {
  private final Table table;
  private boolean cellEvents;

  public TableViewer(Composite parent) {
    this(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
  }

  public TableViewer(Composite parent, int style) {
    this(new Table(parent, style));
  }

  public TableViewer(Table table) {
    this.table = table;
    table.addListener(SWT.Selection, e -> {
      if (e.detail == SWT.CHECK)
        handleCheck(e.item);
      else
        fireSelectionChanged(new SelectionChangedEvent(this, getSelection()));
    });
    table.addListener(SWT.DefaultSelection, e -> fireDoubleClick(new DoubleClickEvent(this, getSelection())));
  }

  /** Check-box tables (CheckboxTableViewer) react to item checks. */
  protected void handleCheck(Widget item) {
  }

  @Override
  void enableCellEvents() {
    if (cellEvents)
      return;
    cellEvents = true;
    ColumnViewerEditor ed = getColumnViewerEditor();
    table.addCellListener(new Table.CellListener() {
      @Override
      public void cellActivated(TableItem item, int column, String trigger) {
        ed.cellActivated(item, column, trigger);
      }

      @Override
      public void cellEditDone(TableItem item, int column, JsonElement value, boolean apply) {
        ed.cellEditDone(item, column, value, apply);
      }
    });
  }

  public Table getTable() {
    return table;
  }

  @Override
  public Control getControl() {
    return table;
  }

  @Override
  protected Object[] columnWidgets() {
    return table.getColumns();
  }

  protected Object[] currentElements() {
    Object input = getInput();
    if (getContentProvider() == null || input == null)
      return new Object[0];
    return getFilteredChildren(input, getRawChildren(input));
  }

  @Override
  public void refresh() {
    if (table.isDisposed())
      return;
    Set<Object> selected = new HashSet<>();
    for (TableItem i : table.getSelection())
      selected.add(i.getData());
    Object[] elements = currentElements();
    List<TableItem> keep = new ArrayList<>();
    for (int i = 0; i < elements.length; i++) {
      TableItem item = i < table.getItemCount() ? table.getItem(i) : new TableItem(table, SWT.NONE);
      item.setData(elements[i]);
      updateItem(item, elements[i]);
      if (selected.contains(elements[i]))
        keep.add(item);
    }
    while (table.getItemCount() > elements.length)
      table.getItem(table.getItemCount() - 1).dispose();
    table.setSelection(keep.toArray(new TableItem[0]));
    afterRefresh();
  }

  protected void afterRefresh() {
  }

  @Override
  public void refresh(Object element) {
    if (element == null || element == getInput()) {
      refresh();
      return;
    }
    update(element, null);
  }

  @Override
  public void update(Object element, String[] properties) {
    for (TableItem i : table.getItems())
      if (same(i.getData(), element))
        updateItem(i, element);
  }

  public void add(Object element) {
    add(new Object[] { element });
  }

  public void add(Object[] elements) {
    if (getComparator() != null || getFilters().length > 0) {
      refresh();
      return;
    }
    for (Object e : elements) {
      TableItem item = new TableItem(table, SWT.NONE);
      item.setData(e);
      updateItem(item, e);
    }
  }

  public void insert(Object element, int position) {
    if (getComparator() != null) {
      refresh();
      return;
    }
    TableItem item = new TableItem(table, SWT.NONE, position < 0 ? table.getItemCount() : Math.min(position, table.getItemCount()));
    item.setData(element);
    updateItem(item, element);
  }

  public void remove(Object element) {
    remove(new Object[] { element });
  }

  public void remove(Object[] elements) {
    for (Object e : elements)
      for (TableItem i : table.getItems())
        if (same(i.getData(), e))
          i.dispose();
  }

  /**
   * {@code org.eclipse.jface.viewers.TableViewer.replace(Object, int)}: shows the element in the row at index.
   */
  public void replace(Object element, int index) {
    TableItem item = table.getItem(index);
    item.setData(element);
    updateItem(item, element);
  }

  /**
   * {@code org.eclipse.jface.viewers.TableViewer.getCell(Point)}: the cell under a point of the last mouse event, or
   * null. The row is {@link Table#getItem(Point)}; the column is hit-tested with the column widths, because the renderer
   * does not report the column of a mouse event. A table without columns has one cell (column 0).
   */
  public ViewerCell getCell(Point point) {
    TableItem item = table.getItem(point);
    if (item == null)
      return null;
    TableColumn[] columns = table.getColumns();
    int column = 0;
    int reported = table.getColumnAt(point);
    if (reported >= 0 && reported < Math.max(1, columns.length))
      return new ViewerCell(item, reported, item.getData());
    if (columns.length > 0) {
      column = -1;
      int x = 0;
      for (int i = 0; i < columns.length && column < 0; i++) {
        x += columns[i].getWidth();
        if (point.x < x)
          column = i;
      }
      if (column < 0)
        return null;
    }
    return new ViewerCell(item, column, item.getData());
  }

  public Object getElementAt(int index) {
    return index >= 0 && index < table.getItemCount() ? table.getItem(index).getData() : null;
  }

  public void setItemCount(int count) {
    table.setItemCount(count);
  }

  @Override
  protected Item findItem(Object element) {
    for (TableItem i : table.getItems())
      if (same(i.getData(), element))
        return i;
    return null;
  }

  @Override
  public ISelection getSelection() {
    if (table.isDisposed())
      return StructuredSelection.EMPTY;
    List<Object> l = new ArrayList<>();
    for (TableItem i : table.getSelection())
      l.add(i.getData());
    return new StructuredSelection(l);
  }

  @Override
  public void setSelection(ISelection selection, boolean reveal) {
    List<TableItem> sel = new ArrayList<>();
    if (selection instanceof IStructuredSelection) {
      for (Object o : ((IStructuredSelection) selection).toList())
        for (TableItem i : table.getItems())
          if (same(i.getData(), o))
            sel.add(i);
    }
    table.setSelection(sel.toArray(new TableItem[0]));
    if (reveal)
      table.showSelection();
    fireSelectionChanged(new SelectionChangedEvent(this, getSelection()));
  }

  @Override
  public void reveal(Object element) {
    Item i = findItem(element);
    if (i instanceof TableItem)
      table.showItem((TableItem) i);
  }
}
