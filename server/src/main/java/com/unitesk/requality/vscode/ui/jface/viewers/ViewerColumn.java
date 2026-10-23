package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ViewerColumn}. */
public abstract class ViewerColumn {
  private final ColumnViewer viewer;
  private CellLabelProvider labelProvider;
  private EditingSupport editingSupport;

  protected ViewerColumn(ColumnViewer viewer) {
    this.viewer = viewer;
  }

  public ColumnViewer getViewer() {
    return viewer;
  }

  public void setLabelProvider(CellLabelProvider lp) {
    labelProvider = lp;
    viewer.labelsChanged();
  }

  public CellLabelProvider getLabelProvider() {
    return labelProvider;
  }

  public void setEditingSupport(EditingSupport es) {
    editingSupport = es;
    viewer.enableCellEvents();
  }

  public EditingSupport getEditingSupport() {
    return editingSupport;
  }
}
