package com.unitesk.requality.vscode.ui.swt.widgets;

/** A layout that sizes table columns (JFace TableLayout); applied whenever the table is synchronized. */
public interface TableAware {
  void apply(Table table);
}
