package com.unitesk.requality.vscode.ui.compare;

import java.io.IOException;
import java.io.InputStream;
import java.util.ResourceBundle;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionDelegate;

/**
 * {@code org.eclipse.compare.internal.AddFromHistoryAction}: Restore from Local History... of a folder or a project. The
 * files deleted from the container that have states in the local history are offered in {@link AddFromHistoryDialog};
 * the checked ones are created again with the selected state.
 * VSCODE: the action is enabled for a single container (a file has nothing to restore into).
 */
public class AddFromHistoryAction extends ActionDelegate {
  private static final String BUNDLE_NAME = "org.eclipse.compare.internal.AddFromHistoryAction";

  private IStructuredSelection selection;

  @Override
  public void selectionChanged(IAction action, ISelection sel) {
    selection = sel instanceof IStructuredSelection ? (IStructuredSelection) sel : null;
    action.setEnabled(container() != null);
  }

  @Override
  public void run(IAction action) {
    IContainer container = container();
    if (container == null)
      return;
    ResourceBundle bundle = CompareBundles.bundle(BUNDLE_NAME);
    String title = CompareBundles.getString(bundle, "title");
    Shell parentShell = EditionAction.shell();

    IFile[] states;
    try {
      states = container.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, new NullProgressMonitor());
    } catch (CoreException ex) {
      RLogger.logError(ex);
      states = new IFile[0];
    }
    // a recently deleted file at the same path as the container is not offered
    if (states.length == 1 && states[0].getFullPath().equals(container.getFullPath()))
      states = new IFile[0];
    if (states.length <= 0) {
      MessageDialog.openInformation(parentShell, title, CompareBundles.getString(bundle, "noLocalHistoryError"));
      return;
    }

    AddFromHistoryDialog dialog = new AddFromHistoryDialog(parentShell, bundle);
    if (dialog.select(container, states)) {
      AddFromHistoryDialog.HistoryInput[] selected = dialog.getSelected();
      if (selected.length > 0)
        restore(bundle, parentShell, selected);
    }
  }

  /** Creates the checked files again with their selected states; the missing folders are created first. */
  private static void restore(ResourceBundle bundle, Shell shell,
      AddFromHistoryDialog.HistoryInput[] selected) {
    try {
      for (AddFromHistoryDialog.HistoryInput s : selected) {
        IFile file = s.fFile;
        createContainers(file);
        try (InputStream in = s.fFileState.getContents()) {
          file.create(in, false, new NullProgressMonitor());
        }
      }
    } catch (CoreException | IOException e) {
      RLogger.logError(e);
      MessageDialog.openError(shell, CompareBundles.getString(bundle, "title"),
          CompareBundles.getFormattedString(bundle, "replaceError", e.getMessage()));
    }
  }

  /** The missing folders of a restored file are created first. */
  static void createContainers(IResource resource) throws CoreException {
    IContainer container = resource.getParent();
    if (container instanceof IFolder parent && !parent.exists()) {
      createContainers(parent);
      parent.create(false, true, null);
    }
  }

  /** The one container of the selection, or null. */
  private IContainer container() {
    if (selection == null || selection.size() != 1)
      return null;
    Object o = selection.getFirstElement();
    return o instanceof IContainer ? (IContainer) o : Adapters.adapt(o, IContainer.class);
  }
}
