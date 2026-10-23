package com.unitesk.requality.vscode.ui.team.history;

import java.text.DateFormat;
import java.util.Date;

import com.unitesk.requality.vscode.ui.jface.viewers.ITableLabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Tree;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeColumn;
import com.unitesk.requality.vscode.ui.team.core.history.IFileRevision;
import com.unitesk.requality.vscode.ui.team.core.history.LocalFileRevision;
import com.unitesk.requality.vscode.ui.compare.IModificationDate;

/**
 * {@code org.eclipse.team.internal.ui.history.LocalHistoryTableProvider}: the tree of the revisions of a local history page
 * with the column "Revision Time", the sorting of the revisions by date and the labels of the revisions and categories.
 */
public class LocalHistoryTableProvider {
  static final int COL_DATE = 0;
  static final String REVISION_TIME = "Revision Time";

  private TreeViewer viewer;
  private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

  /** The date format of the revision times. */
  public DateFormat getDateFormat() {
    return dateFormat;
  }

  /** The image of a revision (none: the file icon comes from the VS Code file theme). */
  public Image getRevisionImage() {
    return null;
  }

  /** The revision of an element of the tree (the revision itself, the adapted revision, or the first of a category). */
  protected IFileRevision adaptToFileRevision(Object element) {
    if (element instanceof IFileRevision revision) {
      return revision;
    }
    if (element instanceof AbstractHistoryCategory category) {
      IFileRevision[] revisions = category.getRevisions();
      return revisions != null && revisions.length > 0 ? revisions[0] : null;
    }
    return Adapters.adapt(element, IFileRevision.class);
  }

  /** The time stamp of an element, or -1. */
  protected long getModificationDate(Object element) {
    IFileRevision entry = adaptToFileRevision(element);
    if (entry != null) {
      return entry.getTimestamp();
    }
    IModificationDate md = Adapters.adapt(element, IModificationDate.class);
    return md != null ? md.getModificationDate() : -1;
  }

  /** The label provider of the tree. */
  private class LocalHistoryLabelProvider extends LabelProvider implements ITableLabelProvider {
    @Override
    public Image getColumnImage(Object element, int columnIndex) {
      return null;
    }

    @Override
    public String getColumnText(Object element, int columnIndex) {
      if (columnIndex == COL_DATE) {
        if (element instanceof AbstractHistoryCategory category) {
          return category.getName();
        }
        long date = getModificationDate(element);
        if (date != -1) {
          return dateFormat.format(new Date(date));
        }
      }
      return "";
    }
  }

  /** Sorts the revisions by their date (most recent first; reversed on the second click). */
  private static final class HistoryComparator extends com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator {
    private boolean reversed;
    private final int columnNumber;
    private final LocalHistoryTableProvider provider;

    HistoryComparator(LocalHistoryTableProvider provider, int columnNumber) {
      this.provider = provider;
      this.columnNumber = columnNumber;
    }

    @Override
    public int compare(com.unitesk.requality.vscode.ui.jface.viewers.Viewer compareViewer, Object o1, Object o2) {
      long date1 = provider.getModificationDate(o1);
      long date2 = provider.getModificationDate(o2);
      int result;
      if (date1 == -1 || date2 == -1) {
        result = super.compare(compareViewer, o1, o2);
      } else if (date1 == date2) {
        result = 0;
      } else {
        result = date1 > date2 ? -1 : 1;
      }
      return reversed ? -result : result;
    }

    int getColumnNumber() {
      return columnNumber;
    }

    boolean isReversed() {
      return reversed;
    }

    void setReversed(boolean newReversed) {
      reversed = newReversed;
    }
  }

  /** Creates the tree of the revisions in the parent (the content provider is set by the page). */
  public TreeViewer createTree(Composite parent, boolean allowMultiSelection) {
    int style = SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION;
    if (allowMultiSelection) {
      style = style | SWT.MULTI;
    }
    Tree tree = new Tree(parent, style);
    tree.setHeaderVisible(true);
    tree.setLinesVisible(false);
    tree.setLayoutData(new GridData(GridData.FILL_BOTH));
    viewer = new TreeViewer(tree);
    TreeColumn col = new TreeColumn(tree, SWT.NONE);
    col.setResizable(true);
    col.setText(REVISION_TIME);
    col.setWidth(260);
    col.addSelectionListener(new com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter() {
      @Override
      public void widgetSelected(com.unitesk.requality.vscode.ui.swt.events.SelectionEvent e) {
        HistoryComparator old = (HistoryComparator) viewer.getComparator();
        if (old != null && old.getColumnNumber() == COL_DATE) {
          old.setReversed(!old.isReversed());
          viewer.refresh();
        } else {
          viewer.setComparator(new HistoryComparator(LocalHistoryTableProvider.this, COL_DATE));
        }
      }
    });
    viewer.setLabelProvider(new LocalHistoryLabelProvider());
    viewer.setComparator(new HistoryComparator(this, COL_DATE));
    return viewer;
  }

  /** Whether the element is the current state of the workspace file. */
  protected boolean isCurrentEdition(Object element) {
    return adaptToFileRevision(element) instanceof LocalFileRevision local && local.isCurrentState();
  }
}
