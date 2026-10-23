package com.unitesk.requality.vscode.ui.team.history;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareEditorInput;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffNode;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.team.FileRevisionTypedElement;
import com.unitesk.requality.vscode.ui.team.LocalResourceTypedElement;
import com.unitesk.requality.vscode.ui.team.core.history.IFileRevision;
import com.unitesk.requality.vscode.ui.team.core.history.LocalFileRevision;
import com.unitesk.requality.vscode.ui.team.ui.history.HistoryPage;

/**
 * {@code org.eclipse.team.internal.ui.actions.CompareRevisionAction}: Compare of the selected revisions of a history page:
 * one revision is compared with the current file (or with the revision it is based on), two revisions with each other.
 * The comparison opens in a compare editor (the VS Code diff editor for texts).
 */
public class CompareRevisionAction extends Action {
  private final HistoryPage page;
  private IStructuredSelection selection;
  private IFileRevision currentFileRevision;

  public CompareRevisionAction(String text, HistoryPage page) {
    super(text);
    this.page = page;
  }

  public CompareRevisionAction(HistoryPage page) {
    this("&Compare", page);
  }

  @Override
  public void run() {
    IStructuredSelection structSel = selection;
    if (structSel == null) {
      return;
    }
    Object[] objArray = structSel.toArray();
    IFileRevision file1;
    IFileRevision file2;
    if (objArray.length == 1 && objArray[0] instanceof IFileRevision revision) {
      file1 = currentFileRevision;
      file2 = revision;
    } else if (objArray.length == 2 && objArray[0] instanceof IFileRevision r0 && objArray[1] instanceof IFileRevision r1) {
      file1 = r0;
      file2 = r1;
    } else {
      return;
    }
    if (file1 == null || file2 == null || !file1.exists() || !file2.exists()) {
      MessageDialog.openError(page.getHistoryPageSite().getShell(), "Deleted Revision",
          "One of the revisions represents a deletion and cannot be compared.");
      return;
    }
    IFile resource2 = fileOf(file2);
    if (resource2 != null) {
      IFileRevision temp = file1;
      file1 = file2;
      file2 = temp;
    }
    IFile resource1 = fileOf(file1);
    ITypedElement left = resource1 != null ? new LocalResourceTypedElement(resource1)
        : new FileRevisionTypedElement(file1, null);
    ITypedElement right = new FileRevisionTypedElement(file2, null);
    openInCompare(left, right);
  }

  /** The file of a revision that is the current workspace file, or null. */
  private static IFile fileOf(IFileRevision revision) {
    return revision instanceof LocalFileRevision local ? local.getFile() : null;
  }

  /**
   * {@code CompareRevisionAction.openInCompare}: the compare editor of a {@code CompareFileRevisionEditorInput} of the two
   * sides, titled "Compare {0} {1} and {2}" (the file name and the identifiers of the sides) and labeled "Local: {0}"
   * and "Local history: {0} {1}".
   */
  private void openInCompare(ITypedElement left, ITypedElement right) {
    CompareConfiguration config = new CompareConfiguration();
    config.setLeftLabel(revisionLabel(left));
    config.setRightLabel(revisionLabel(right));
    config.setLeftEditable(left instanceof LocalResourceTypedElement);
    config.setRightEditable(right instanceof LocalResourceTypedElement);
    CompareEditorInput input = new CompareEditorInput(config) {
      @Override
      protected Object prepareInput(IProgressMonitor monitor) {
        return new DiffNode(null, Differencer.CHANGE, null, left, right);
      }
    };
    input.setTitle(java.text.MessageFormat.format("Compare {0} {1} and {2}", left.getName(), contentIdentifier(left),
        contentIdentifier(right)));
    CompareUI.openCompareEditor(input);
  }

  /** {@code CompareFileRevisionEditorInput.getFileRevisionLabel} / {@code getLocalResourceRevisionLabel}. */
  private static String revisionLabel(ITypedElement element) {
    if (element instanceof FileRevisionTypedElement e && e.getRevision() != null) {
      IFileRevision revision = e.getRevision();
      if (revision instanceof LocalFileRevision local && local.getState() != null)
        return java.text.MessageFormat.format("Local history: {0} {1}", element.getName(),
            java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(revision.getTimestamp())));
      return java.text.MessageFormat.format("{0} {1}", element.getName(), revision.getContentIdentifier());
    }
    return java.text.MessageFormat.format("Local: {0}", element.getName());
  }

  /** {@code CompareFileRevisionEditorInput.getContentIdentifier}: "Local Revision", "Current Revision" or "Current". */
  private static String contentIdentifier(ITypedElement element) {
    if (element instanceof FileRevisionTypedElement e && e.getRevision() != null) {
      IFileRevision revision = e.getRevision();
      if (revision instanceof LocalFileRevision local)
        return local.getState() != null ? "Local Revision" : "Current Revision";
      return revision.getContentIdentifier();
    }
    return "Current";
  }

  /** The current file revision the revisions are compared with (the file itself). */
  public void setCurrentFileRevision(IFileRevision fileRevision) {
    this.currentFileRevision = fileRevision;
  }

  /**
   * {@code CompareRevisionAction.updateSelection}: the selection of the history page; the text says what is compared
   * ("Compare Current with Local" for a local history state, "Compare with Each Other" for two revisions).
   */
  public void selectionChanged(IStructuredSelection sel) {
    this.selection = sel;
    if (sel != null && sel.size() == 1) {
      Object el = sel.getFirstElement();
      if (el instanceof com.unitesk.requality.vscode.ui.team.core.history.LocalFileRevision)
        setText("Compare Current with Local");
      else if (el instanceof com.unitesk.requality.vscode.ui.team.core.history.provider.FileRevision)
        setText(java.text.MessageFormat.format("Compare Current with {0}",
            ((com.unitesk.requality.vscode.ui.team.core.history.provider.FileRevision) el).getContentIdentifier()));
      else
        setText("&Compare with Current Revision");
      setEnabled(shouldShow());
    } else if (sel != null && sel.size() == 2) {
      setText("&Compare with Each Other");
      setEnabled(shouldShow());
    } else {
      setEnabled(false);
    }
  }

  private boolean shouldShow() {
    if (selection == null || selection.size() == 0 || selection.size() > 2) {
      return false;
    }
    for (Object obj : selection.toArray()) {
      if (!(obj instanceof IFileRevision revision) || !revision.exists()) {
        return false;
      }
    }
    return true;
  }
}
