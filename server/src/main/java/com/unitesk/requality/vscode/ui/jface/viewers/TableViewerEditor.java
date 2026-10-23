package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.TableViewerEditor}. */
public final class TableViewerEditor {
  private TableViewerEditor() {
  }

  public static void create(TableViewer viewer, Object focusCellManager, ColumnViewerEditorActivationStrategy strategy,
      int feature) {
    viewer.getColumnViewerEditor().setStrategy(strategy);
  }

  public static void create(TableViewer viewer, ColumnViewerEditorActivationStrategy strategy, int feature) {
    create(viewer, null, strategy, feature);
  }
}
