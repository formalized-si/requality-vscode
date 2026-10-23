package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.swt.widgets.Item;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeItem;

/** {@code org.eclipse.jface.viewers.ColumnViewer}: per-column label providers and cell editing. */
public abstract class ColumnViewer extends StructuredViewer {
  private final Map<Object, ViewerColumn> columnsByWidget = new HashMap<>();
  private final ColumnViewerEditor editor = new ColumnViewerEditor(this);
  private String[] columnProperties;
  private CellEditor[] cellEditors;
  private ICellModifier cellModifier;

  /** Column widgets in display order. */
  protected abstract Object[] columnWidgets();

  void registerColumn(ViewerColumn vc, Object widget) {
    columnsByWidget.put(widget, vc);
  }

  public ViewerColumn getViewerColumn(int index) {
    Object[] cols = columnWidgets();
    return index >= 0 && index < cols.length ? columnsByWidget.get(cols[index]) : null;
  }

  int columnCount() {
    return Math.max(1, columnWidgets().length);
  }

  void labelsChanged() {
    if (getInput() != null && getControl() != null && !getControl().isDisposed())
      refresh();
  }

  abstract void enableCellEvents();

  /** Applies labels, images, colors and fonts of every column to an item. */
  protected void updateItem(Item item, Object element) {
    int n = columnCount();
    for (int i = 0; i < n; i++) {
      ViewerColumn vc = getViewerColumn(i);
      if (vc != null && vc.getLabelProvider() != null) {
        vc.getLabelProvider().update(new ViewerCell(item, i, element));
        continue;
      }
      if (vc == null && getLabelProvider() instanceof CellLabelProvider) {
        ((CellLabelProvider) getLabelProvider()).update(new ViewerCell(item, i, element));
        continue;
      }
      ViewerCell cell = new ViewerCell(item, i, element);
      cell.setText(text(element, i));
      cell.setImage(image(element, i));
      cell.setForeground(foreground(element, i));
      cell.setBackground(background(element, i));
    }
    if (item instanceof TableItem)
      ((TableItem) item).setFont(font(element));
    else if (item instanceof TreeItem)
      ((TreeItem) item).setFont(font(element));
  }

  public ColumnViewerEditor getColumnViewerEditor() {
    return editor;
  }

  public void editElement(Object element, int column) {
    editor.editElement(this, element, column);
  }

  public void cancelEditing() {
    editor.cancel();
  }

  public boolean isCellEditorActive() {
    return editor.isActive();
  }

  public void setColumnProperties(String[] props) {
    columnProperties = props;
  }

  public String[] getColumnProperties() {
    return columnProperties;
  }

  public void setCellEditors(CellEditor[] editors) {
    cellEditors = editors;
    enableCellEvents();
  }

  public CellEditor[] getCellEditors() {
    return cellEditors;
  }

  public void setCellModifier(ICellModifier m) {
    cellModifier = m;
    enableCellEvents();
  }

  public ICellModifier getCellModifier() {
    return cellModifier;
  }

  /** Item showing the element (first match). */
  protected abstract Item findItem(Object element);
}
