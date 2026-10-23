package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.EditingSupport}. */
public abstract class EditingSupport {
  private final ColumnViewer viewer;

  protected EditingSupport(ColumnViewer viewer) {
    this.viewer = viewer;
  }

  protected abstract CellEditor getCellEditor(Object element);

  protected abstract boolean canEdit(Object element);

  protected abstract Object getValue(Object element);

  protected abstract void setValue(Object element, Object value);

  public ColumnViewer getViewer() {
    return viewer;
  }

  protected void initializeCellEditorValue(CellEditor cellEditor, ViewerCell cell) {
    cellEditor.setValue(getValue(cell.getElement()));
  }

  protected void saveCellEditorValue(CellEditor cellEditor, ViewerCell cell) {
    setValue(cell.getElement(), cellEditor.getValue());
  }
}
