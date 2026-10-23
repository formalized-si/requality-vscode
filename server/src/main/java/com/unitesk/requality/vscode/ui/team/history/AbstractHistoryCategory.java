package com.unitesk.requality.vscode.ui.team.history;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.team.core.history.IFileRevision;

/**
 * {@code org.eclipse.team.internal.ui.history.AbstractHistoryCategory}: a category (a group of revisions, such as the
 * date groups of the history page) of the tree of the history page.
 */
public abstract class AbstractHistoryCategory {
  /** The name of this category. */
  public abstract String getName();

  /** The image next to the name, or null. */
  public Image getImage() {
    return null;
  }

  /** Whether this category has any revisions. */
  public abstract boolean hasRevisions();

  /**
   * Collects the revisions that belong to this category.
   *
   * @return whether any revision matches this category
   */
  public abstract boolean collectFileRevisions(IFileRevision[] fileRevisions, boolean shouldRemove);

  /** The revisions of this category, or null when there are none. */
  public abstract IFileRevision[] getRevisions();
}
