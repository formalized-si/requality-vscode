package com.unitesk.requality.vscode.ui.team.history;

import com.unitesk.requality.vscode.ui.compare.CompareEditor;
import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.team.FileRevisionTypedElement;
import com.unitesk.requality.vscode.ui.team.core.history.IFileRevision;
import com.unitesk.requality.vscode.ui.team.ui.history.HistoryPage;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.team.internal.ui.actions.OpenRevisionAction}: Open of the selected revisions of a history page. A
 * revision opens read-only ({@link CompareEditor#openContent}, the VS Code editor of its contents); the current
 * revision opens the workspace file in its editor.
 */
public class OpenRevisionAction extends Action {
  private IStructuredSelection selection;
  private final HistoryPage page;

  public OpenRevisionAction(String text, HistoryPage page) {
    super(text);
    this.page = page;
  }

  @Override
  public void run() {
    IStructuredSelection structSel = selection;
    if (structSel == null) {
      return;
    }
    for (Object tempRevision : structSel.toArray()) {
      if (tempRevision instanceof AbstractHistoryCategory) {
        continue;
      }
      IFileRevision revision = (IFileRevision) tempRevision;
      if (revision == null || !revision.exists()) {
        MessageDialog.openError(page.getHistoryPageSite().getShell(), "Deleted Revision",
            "This revision represents a deletion and cannot be opened.");
      } else {
        openEditor(revision);
      }
    }
  }

  /**
   * {@code Utils.openEditor(IWorkbenchPage, IFileRevision, IProgressMonitor)}: the current revision is the workspace
   * file and opens in its editor; another revision opens read-only ({@code FileRevisionEditorInput}).
   */
  private void openEditor(IFileRevision revision) {
    try {
      IStorage storage = revision.getStorage(new NullProgressMonitor());
      if (storage instanceof IFile file) {
        IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), file, true);
        return;
      }
      CompareEditor.openContent(revision.getName() + " " + LocalHistoryPage.revisionLabel(revision),
          new FileRevisionTypedElement(revision, null));
    } catch (CoreException e) {
      MessageDialog.openError(page.getHistoryPageSite().getShell(), "Open Revision",
          "An error occurred while trying to open the revision.");
    }
  }

  /** Sets the selection of the history page and enables the action when its revisions can be opened. */
  public void selectionChanged(IStructuredSelection sel) {
    this.selection = sel;
    boolean show = sel.size() > 0;
    for (Object obj : sel.toArray()) {
      if (obj instanceof AbstractHistoryCategory || (obj instanceof IFileRevision r && !r.exists())) {
        show = false;
      }
    }
    setEnabled(show);
  }
}
