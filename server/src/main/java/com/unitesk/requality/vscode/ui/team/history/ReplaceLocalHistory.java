package com.unitesk.requality.vscode.ui.team.history;

import java.io.InputStream;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.CompareViewerPane;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFileState;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.team.FileRevisionTypedElement;
import com.unitesk.requality.vscode.ui.team.ui.history.HistoryPageCompareEditorInput;
import com.unitesk.requality.vscode.ui.workbench.IPage;

/**
 * {@code org.eclipse.team.internal.ui.history.ReplaceLocalHistory}: Replace With &gt; Local History... of a file. The local
 * history page is shown in a compare dialog (the edition selection dialog): the selected state replaces the file on
 * Replace (the file's contents are kept in the local history).
 */
public class ReplaceLocalHistory extends ShowLocalHistory {
  @Override
  public void run(IAction action) {
    final IFile file = (IFile) getSelection().getFirstElement();
    IFileState[] states = getLocalHistory();
    if (states == null || states.length == 0) {
      return;
    }
    Display.getDefault().asyncExec(() -> showCompareInDialog(file));
  }

  private void showCompareInDialog(IFile file) {
    LocalHistoryPageSource pageSource = (LocalHistoryPageSource) LocalHistoryPageSource.getInstance();
    if (!pageSource.canShowHistoryFor(file)) {
      return;
    }
    CompareConfiguration cc = new CompareConfiguration();
    cc.setLeftEditable(false);
    cc.setRightEditable(false);
    HistoryPageCompareEditorInput input = new HistoryPageCompareEditorInput(cc, pageSource, file) {
      @Override
      public boolean isEditionSelectionDialog() {
        return true;
      }

      @Override
      public String getOKButtonLabel() {
        return "&Replace";
      }

      @Override
      protected IPage createPage(CompareViewerPane parent, ToolBarManager toolBarManager) {
        return super.createPage(parent, toolBarManager, false);
      }

      @Override
      public boolean okPressed() {
        try {
          Object o = getSelectedEdition();
          if (!(o instanceof ICompareInput ci) || !(ci.getRight() instanceof FileRevisionTypedElement right)) {
            return false;
          }
          try (InputStream contents = right.getContents()) {
            file.setContents(contents, false, true, new NullProgressMonitor());
          }
        } catch (CoreException | java.io.IOException e) {
          MessageDialog.openError(getShell(), getPromptTitle(), e.getMessage());
          return false;
        }
        return true;
      }
    };
    CompareUI.openCompareDialog(input);
  }

  @Override
  protected String getPromptTitle() {
    return "Replace with Local History";
  }
}
