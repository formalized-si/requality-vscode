package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.swt.widgets.TableColumn;

/** {@code org.eclipse.jface.viewers.TableViewerColumn}. */
public class TableViewerColumn extends ViewerColumn {
  private final TableColumn column;

  public TableViewerColumn(TableViewer viewer, int style) {
    this(viewer, style, -1);
  }

  public TableViewerColumn(TableViewer viewer, int style, int index) {
    this(viewer, index < 0 ? new TableColumn(viewer.getTable(), style) : new TableColumn(viewer.getTable(), style, index));
  }

  public TableViewerColumn(TableViewer viewer, TableColumn column) {
    super(viewer);
    this.column = column;
    viewer.registerColumn(this, column);
  }

  public TableColumn getColumn() {
    return column;
  }
}
