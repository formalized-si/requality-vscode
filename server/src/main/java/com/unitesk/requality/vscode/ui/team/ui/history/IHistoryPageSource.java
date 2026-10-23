package com.unitesk.requality.vscode.ui.team.ui.history;

import com.unitesk.requality.vscode.ui.workbench.Page;

/** {@code org.eclipse.team.ui.history.IHistoryPageSource}: supplies the history page of an object. */
public interface IHistoryPageSource {
  /** Whether this source can show the history of the object. */
  boolean canShowHistoryFor(Object object);

  /** Creates the page (an {@link IHistoryPage}) that shows the history of the object. */
  Page createPage(Object object);
}
