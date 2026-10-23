package com.unitesk.requality.vscode.ui.team.wizards;

import java.lang.reflect.InvocationTargetException;

import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.MultiStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.workbench.IImportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.team.internal.ui.wizards.ProjectSetImportWizard}: File &gt; Import &gt; Team &gt; Team Project Set, the
 * wizard that imports the projects of a Team Project Set file.
 */
public class ProjectSetImportWizard extends Wizard implements IImportWizard {
  private static final String PLUGIN_ID = "org.eclipse.team.ui";

  private ImportProjectSetMainPage mainPage;

  public ProjectSetImportWizard() {
    setNeedsProgressMonitor(true);
    setWindowTitle("Team Project Set");
  }

  @Override
  public void addPages() {
    mainPage = new ImportProjectSetMainPage("projectSetMainPage", "Import a Team Project Set", null);
    addPage(mainPage);
  }

  @Override
  public boolean performFinish() {
    boolean result = false;
    try {
      ImportProjectSetOperation op;
      if (mainPage.getInputType() == ImportProjectSetMainPage.InputType_URL) {
        String psfContent = mainPage.getURLContents();
        if (psfContent == null)
          return false;
        op = new ImportProjectSetOperation(mainPage.isRunInBackgroundOn() ? null : getContainer(), psfContent,
            mainPage.getUrl(), mainPage.getWorkingSets());
      } else {
        op = new ImportProjectSetOperation(mainPage.isRunInBackgroundOn() ? null : getContainer(),
            mainPage.getFileName(), mainPage.getWorkingSets());
      }
      op.run();
      result = true;
    } catch (InterruptedException e) {
      return true;
    } catch (InvocationTargetException e) {
      Throwable target = e.getTargetException();
      // the runnable context may wrap the exception of the import once more
      while (target instanceof InvocationTargetException && ((InvocationTargetException) target).getTargetException() != null)
        target = ((InvocationTargetException) target).getTargetException();
      if (target instanceof CoreException) {
        ErrorDialog.openError(getShell(), null, null, withChildren(((CoreException) target).getStatus()));
        return false;
      }
      if (target instanceof RuntimeException)
        throw (RuntimeException) target;
      if (target instanceof Error)
        throw (Error) target;
      IStatus status = new Status(IStatus.ERROR, PLUGIN_ID, 0,
          "An error occurred while performing the project set import: " + (target == null ? "" : target.getMessage()),
          target);
      ErrorDialog.openError(getShell(), null, null, status);
    }
    return result;
  }

  /**
   * The status with the messages of its children on separate lines: the error dialog shows one message, and the errors of
   * the single providers are the details of the multi status (the Eclipse error dialog lists them).
   * VSCODE: the children are part of the message of the dialog.
   */
  static IStatus withChildren(IStatus status) {
    if (!(status instanceof MultiStatus))
      return status;
    StringBuilder sb = new StringBuilder(String.valueOf(status.getMessage()));
    for (IStatus child : ((MultiStatus) status).getChildren())
      sb.append('\n').append(child.getMessage());
    return new Status(status.getSeverity(), PLUGIN_ID, 0, sb.toString(), status.getException());
  }

  /**
   * {@code ProjectSetImportWizard.init}: a selected .psf file of the workbench is the default file of the import
   * ({@code PsfFilenameStore.setDefaultFromSelection}).
   */
  @Override
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    PsfHistory.setDefaultFromSelection(workbench);
  }
}
