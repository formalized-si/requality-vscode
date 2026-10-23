package com.unitesk.requality.vscode.ui.team.history;

import com.unitesk.requality.vscode.ui.team.core.history.IFileRevision;

/** {@code org.eclipse.team.internal.ui.history.MessageHistoryCategory}: a category that shows a message (no revisions). */
public class MessageHistoryCategory extends AbstractHistoryCategory {
  private final String message;

  public MessageHistoryCategory(String message) {
    this.message = message;
  }

  @Override
  public String getName() {
    return message;
  }

  @Override
  public boolean collectFileRevisions(IFileRevision[] fileRevisions, boolean shouldRemove) {
    return false;
  }

  @Override
  public IFileRevision[] getRevisions() {
    return new IFileRevision[0];
  }

  @Override
  public boolean hasRevisions() {
    return false;
  }
}
