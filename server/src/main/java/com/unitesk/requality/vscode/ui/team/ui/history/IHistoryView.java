package com.unitesk.requality.vscode.ui.team.ui.history;

/** {@code org.eclipse.team.ui.history.IHistoryView}: a view that shows the history of an object (the History view). */
public interface IHistoryView {
  /** The id of the History view. */
  String VIEW_ID = "org.eclipse.team.ui.GenericHistoryView";

  /** The history page that the view displays. */
  IHistoryPage getHistoryPage();

  /** Shows the history of the object (not forced: the view may be pinned). */
  IHistoryPage showHistoryFor(Object object);

  /** Shows the history of the object; when forced it is shown in this view even when it is pinned. */
  IHistoryPage showHistoryFor(Object object, boolean force);
}
