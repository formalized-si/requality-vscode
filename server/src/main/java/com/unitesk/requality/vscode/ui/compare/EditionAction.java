package com.unitesk.requality.vscode.ui.compare;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.ResourceBundle;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFileState;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.team.FileRevisionTypedElement;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionDelegate;

/**
 * {@code org.eclipse.compare.internal.EditionAction}: the actions on the local history of a file (Compare With &gt; Local
 * History..., Replace With &gt; Local History... and Previous from Local History). The editions are the file itself and
 * its local history states; the selected one is compared with the file or replaces its contents (the replaced contents
 * are kept in the local history). The previous action replaces the file with the most recent edition that differs.
 * VSCODE: the target is the file on disk (there is no editor buffer of unsaved text to compare).
 */
public class EditionAction extends ActionDelegate {
  private final boolean fReplaceMode;
  private final String fBundleName;
  /** Replace With &gt; Previous from Local History: no dialog, the most recent edition that differs is used. */
  protected boolean fPrevious = false;
  private IStructuredSelection selection;

  EditionAction(boolean replaceMode, String bundleName) {
    fReplaceMode = replaceMode;
    fBundleName = bundleName;
  }

  @Override
  public void selectionChanged(IAction action, ISelection sel) {
    selection = sel instanceof IStructuredSelection ? (IStructuredSelection) sel : null;
    action.setEnabled(file() != null);
  }

  @Override
  public void run(IAction action) {
    IFile file = file();
    if (file != null)
      doFromHistory(file);
  }

  /** The one file of the selection (the file itself or the file of its mapping), or null. */
  protected IFile file() {
    if (selection == null || selection.size() != 1)
      return null;
    return CompareAction.file(selection.getFirstElement());
  }

  /** The shell of the dialogs. */
  public static Shell shell() {
    return Display.getDefault().getActiveShell();
  }

  private void doFromHistory(final IFile file) {
    ResourceBundle bundle = CompareBundles.bundle(fBundleName);
    String title = CompareBundles.getString(bundle, "title");
    Shell parentShell = shell();

    IFileState[] states;
    try {
      states = file.getHistory(null);
    } catch (CoreException ex) {
      MessageDialog.openError(parentShell, title, ex.getMessage());
      return;
    }
    if (states == null || states.length <= 0) {
      MessageDialog.openInformation(parentShell, title, CompareBundles.getString(bundle, "noLocalHistoryError"));
      return;
    }

    // the file itself, then its states (the most recent first)
    ITypedElement base = new ResourceNode(file);
    ITypedElement[] editions = new ITypedElement[states.length + 1];
    editions[0] = base;
    for (int i = 0; i < states.length; i++)
      editions[i + 1] = new HistoryItem(base, states[i]);

    EditionSelectionDialog d = new EditionSelectionDialog(parentShell, bundle);
    d.setEditionTitleArgument(file.getName());
    d.setEditionTitleImage(CompareUI.getImage(file));

    if (fReplaceMode) {
      ITypedElement ti = fPrevious ? d.selectPreviousEdition(base, editions) : d.selectEdition(base, editions);
      if (ti instanceof IStreamContentAccessor sa)
        replace(bundle, parentShell, sa, file);
    } else {
      d.setCompareMode(true);
      d.selectEdition(base, editions);
    }
  }

  /** The label of a state: its modification date and time. */
  public static String label(IFileState state) {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date(state.getModificationTime()));
  }

  /** The state as a compare side (its contents are read when they are compared). */
  public static FileRevisionTypedElement edition(IFileState state) {
    return new FileRevisionTypedElement(state.getName(), "", () -> {
      try {
        return state.getContents();
      } catch (CoreException e) {
        RLogger.logError(e);
        return new ByteArrayInputStream(new byte[0]);
      }
    }, state.getFullPath());
  }

  /** Replaces the contents of the file with the edition; the contents replaced are kept in the local history. */
  private static void replace(ResourceBundle bundle, Shell shell, IStreamContentAccessor sa, IFile file) {
    String title = CompareBundles.getString(bundle, "title");
    if (!Utilities.validateResource(file, shell, title))
      return;
    try (InputStream contents = sa.getContents()) {
      file.setContents(contents, IResource.KEEP_HISTORY, new NullProgressMonitor());
    } catch (CoreException | IOException e) {
      RLogger.logError(e);
      MessageDialog.openError(shell, title,
          CompareBundles.getFormattedString(bundle, "replaceError", e.getMessage()));
    }
  }
}
