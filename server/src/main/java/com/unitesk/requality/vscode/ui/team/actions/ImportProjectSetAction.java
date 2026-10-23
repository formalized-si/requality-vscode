package com.unitesk.requality.vscode.ui.team.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;

import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.team.ProjectSetImporter;
import com.unitesk.requality.vscode.ui.team.preferences.IPreferenceIds;
import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;
import com.unitesk.requality.vscode.ui.team.wizards.ImportProjectSetOperation;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IObjectActionDelegate;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionDelegate;

/**
 * {@code org.eclipse.team.internal.ui.actions.ImportProjectSetAction}: Team &gt; Import Project Set... The selected
 * Team Project Set files are imported with their projects; an error is reported in a dialog.
 * With the run-in-background preference the import runs in a background job.
 */
public class ImportProjectSetAction extends ActionDelegate implements IObjectActionDelegate {
  private static final String PLUGIN_ID = "org.eclipse.team.ui";
  private IStructuredSelection fSelection;

  @Override
  public void run(IAction action) {
    final Shell shell = Display.getDefault().getActiveShell();
    try {
      new ProgressMonitorDialog(shell).run(true, true, monitor -> {
        Iterator<?> iterator = fSelection.iterator();
        while (iterator.hasNext()) {
          IFile file = (IFile) iterator.next();
          if (isRunInBackgroundPreferenceOn()) {
            new ImportProjectSetOperation(null, file.getLocation().toOSString(), new IWorkingSet[0]).run();
          } else {
            ProjectSetImporter.importProjectSet(file.getLocation().toOSString(), shell, monitor);
          }
        }
      });
    } catch (InvocationTargetException exception) {
      Throwable target = exception.getTargetException();
      IStatus status = new Status(IStatus.ERROR, PLUGIN_ID, detail(target), target);
      ErrorDialog.openError(shell, "Problems Occurred", "An error occurred importing this project set.", status);
    } catch (InterruptedException exception) {
      // canceled by the user
    }
  }

  private static boolean isRunInBackgroundPreferenceOn() {
    return TeamUIPlugin.getPreferenceStore().getBoolean(IPreferenceIds.RUN_IMPORT_IN_BACKGROUND);
  }

  /** The text of the error: the messages of the status (and of its children), one per line. */
  static String detail(Throwable t) {
    if (t instanceof CoreException && ((CoreException) t).getStatus() != null)
      return statusText(((CoreException) t).getStatus());
    return String.valueOf(t == null ? "" : t.getMessage());
  }

  private static String statusText(IStatus s) {
    StringBuilder sb = new StringBuilder(String.valueOf(s.getMessage()));
    if (s instanceof com.unitesk.requality.vscode.ui.runtime.MultiStatus)
      for (IStatus child : ((com.unitesk.requality.vscode.ui.runtime.MultiStatus) s).getChildren())
        sb.append('\n').append(statusText(child));
    return sb.toString();
  }

  @Override
  public void selectionChanged(IAction action, ISelection sel) {
    if (sel instanceof IStructuredSelection)
      fSelection = (IStructuredSelection) sel;
  }

  @Override
  public void setActivePart(IAction action, IWorkbenchPart targetPart) {
  }
}
