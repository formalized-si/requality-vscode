package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.ltk.Change;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.ltk.participants.CheckConditionsContext;
import com.unitesk.requality.vscode.ui.ltk.resource.RenameResourceProcessor;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/**
 * The rename of a project run outside the wizard: the rename refactoring of {@link RenameResourceProcessor} (its rename
 * participants of plugin.xml check the conditions, their pre-changes run, the project gets its new name and their
 * changes run). The wizard path is {@code RenameResourceWizard}; this is its workspace-job form.
 */
public final class RenameProjectRefactoring {
  private RenameProjectRefactoring() {
  }

  /** Runs the refactoring in a workspace job, as the refactoring wizard runs it outside the UI thread. */
  public static void schedule(IProject project, String oldName, String newName) {
    WorkspaceJob j = new WorkspaceJob("Rename " + oldName + " to " + newName) {
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        try {
          RefactoringStatus st = RenameProjectRefactoring.run(project, newName, monitor);
          if (st.hasFatalError())
            report(st.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        } catch (CoreException | RuntimeException e) {
          RLogger.logError(e);
          report(e.getMessage());
        }
        return Status.OK_STATUS;
      }
    };
    j.schedule();
  }

  private static void report(String message) {
    Display.getDefault().asyncExec(() -> MessageDialog.openError(null, "Rename Resource",
        message == null || message.isEmpty() ? "The project cannot be renamed now." : message));
  }

  /** Checks the conditions of the rename and performs its change; returns the status of the conditions. */
  public static RefactoringStatus run(IProject project, String newName, IProgressMonitor pm) throws CoreException {
    RenameResourceProcessor processor = new RenameResourceProcessor(project);
    processor.setNewResourceName(newName);
    RefactoringStatus status = processor.checkFinalConditions(pm, new CheckConditionsContext());
    if (status.hasFatalError())
      return status;
    Change change = processor.createChange(pm);
    change.perform(pm);
    return status;
  }
}
