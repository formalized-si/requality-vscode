package com.unitesk.requality.vscode.ui.team.history;

import com.unitesk.requality.vscode.ui.compare.CompareAction;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFileState;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.team.ui.TeamUI;
import com.unitesk.requality.vscode.ui.team.ui.history.IHistoryView;
import com.unitesk.requality.vscode.ui.workbench.IObjectActionDelegate;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionDelegate;

/**
 * {@code org.eclipse.team.internal.ui.history.ShowLocalHistory}: Team &gt; Show Local History of a file. The History view
 * shows the local history page of the file (the states of the file, the most recent first).
 */
public class ShowLocalHistory extends ActionDelegate implements IObjectActionDelegate {
  private IStructuredSelection selection;
  private IWorkbenchPart targetPart;

  @Override
  public void run(IAction action) {
    IFileState[] states = getLocalHistory();
    if (states == null || states.length == 0) {
      return;
    }
    final IFile resource = (IFile) getSelection().getFirstElement();
    Display.getDefault().asyncExec(() -> {
      IHistoryView view = TeamUI.showHistoryFor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
          resource, LocalHistoryPageSource.getInstance());
      if (view != null && view.getHistoryPage() instanceof LocalHistoryPage historyPage) {
        historyPage.setClickAction(isCompare());
      }
    });
  }

  @Override
  public void selectionChanged(IAction action, ISelection sel) {
    if (sel instanceof IStructuredSelection structuredSelection) {
      selection = structuredSelection;
    }
    action.setEnabled(selection != null && CompareAction.file(selection.getFirstElement()) != null);
  }

  @Override
  public void setActivePart(IAction action, IWorkbenchPart part) {
    this.targetPart = part;
  }

  protected Shell getShell() {
    if (targetPart != null) {
      return targetPart.getSite().getShell();
    }
    return Display.getDefault().getActiveShell();
  }

  /** Whether the page is opened in Compare mode (Compare With &gt; Local History...). */
  protected boolean isCompare() {
    return false;
  }

  public IStructuredSelection getSelection() {
    return selection;
  }

  /** The states of the selected file (most recent first), or null when the history could not be read. */
  protected IFileState[] getLocalHistory() {
    final IFile file = (IFile) getSelection().getFirstElement();
    IFileState[] states;
    try {
      states = file.getHistory(null);
    } catch (CoreException ex) {
      MessageDialog.openError(getShell(), getPromptTitle(), ex.getMessage());
      return null;
    }
    if (states == null || states.length <= 0) {
      MessageDialog.openInformation(getShell(), getPromptTitle(), "No local history available for selected resource.");
      return states;
    }
    return states;
  }

  protected String getPromptTitle() {
    return "Show Local History";
  }
}
