package com.unitesk.requality.vscode.ui.team.history;

import java.util.ArrayList;
import java.util.Calendar;

import com.unitesk.requality.vscode.ui.team.core.history.IFileRevision;

/**
 * {@code org.eclipse.team.internal.ui.history.DateHistoryCategory}: the revisions of a range of days ("Today", "Yesterday",
 * "This Month", "Older than This Month").
 */
public class DateHistoryCategory extends AbstractHistoryCategory {
  private final String name;
  private final Calendar fromDate;
  private final Calendar toDate;
  private IFileRevision[] revisions;

  /**
   * Creates a category.
   *
   * @param name the name of this category
   * @param fromDate the start date of the category, or null for everything up to the end date
   * @param toDate the end point of the category, or null for the day of the start date only
   */
  public DateHistoryCategory(String name, Calendar fromDate, Calendar toDate) {
    this.name = name;
    this.fromDate = fromDate;
    this.toDate = toDate;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean collectFileRevisions(IFileRevision[] fileRevisions, boolean shouldRemove) {
    ArrayList<IFileRevision> pertinentRevisions = new ArrayList<>();
    for (IFileRevision fileRevision : fileRevisions) {
      Calendar fileRevDate = Calendar.getInstance();
      fileRevDate.setTimeInMillis(fileRevision.getTimestamp());
      int fileRevDay = fileRevDate.get(Calendar.DAY_OF_YEAR);
      int fileRevYear = fileRevDate.get(Calendar.YEAR);
      if (fromDate == null) {
        // the revision is before the end date
        if ((fileRevDay < toDate.get(Calendar.DAY_OF_YEAR) && fileRevYear == toDate.get(Calendar.YEAR))
            || fileRevYear < toDate.get(Calendar.YEAR)) {
          pertinentRevisions.add(fileRevision);
        }
      } else if (toDate == null) {
        // the revision is on the day of the start date
        if (fileRevDay == fromDate.get(Calendar.DAY_OF_YEAR) && fileRevYear == fromDate.get(Calendar.YEAR)) {
          pertinentRevisions.add(fileRevision);
        }
      } else if (fileRevYear >= fromDate.get(Calendar.YEAR) && fileRevYear <= toDate.get(Calendar.YEAR)
          && fileRevDay >= fromDate.get(Calendar.DAY_OF_YEAR) && fileRevDay < toDate.get(Calendar.DAY_OF_YEAR)) {
        pertinentRevisions.add(fileRevision);
      }
    }
    if (pertinentRevisions.size() > 0) {
      revisions = pertinentRevisions.toArray(new IFileRevision[0]);
      return true;
    }
    return false;
  }

  @Override
  public IFileRevision[] getRevisions() {
    return revisions;
  }

  @Override
  public boolean hasRevisions() {
    return revisions != null && revisions.length != 0;
  }
}
