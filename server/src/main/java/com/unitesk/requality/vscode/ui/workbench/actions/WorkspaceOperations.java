package com.unitesk.requality.vscode.ui.workbench.actions;

import java.lang.reflect.InvocationTargetException;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code WorkspaceModifyOperation} run in a {@code ProgressMonitorDialog}, as the IDE resource actions do; a failure
 * is shown in an error dialog with the action's problem title.
 */
final class WorkspaceOperations {
  interface Body {
    void run(IProgressMonitor monitor) throws CoreException;
  }

  private WorkspaceOperations() {
  }

  /** Runs the operation with progress; false when it failed (the error was shown) or was canceled. */
  static boolean run(Shell shell, String task, String problemsTitle, String problemsMessage, Body body) {
    try {
      new ProgressMonitorDialog(shell).run(true, true, monitor -> {
        monitor.beginTask(task, IProgressMonitor.UNKNOWN);
        try {
          body.run(monitor);
        } catch (CoreException e) {
          throw new InvocationTargetException(e);
        } finally {
          monitor.done();
        }
      });
      return true;
    } catch (InterruptedException e) {
      return false;
    } catch (InvocationTargetException e) {
      Throwable t = e.getTargetException();
      RLogger.logError(t instanceof Exception ? (Exception) t : new RuntimeException(t));
      if (t instanceof CoreException)
        ErrorDialog.openError(shell, problemsTitle, problemsMessage, ((CoreException) t).getStatus());
      else
        MessageDialog.openError(shell, problemsTitle, problemsMessage + "\n" + t.getMessage());
      return false;
    }
  }
}
