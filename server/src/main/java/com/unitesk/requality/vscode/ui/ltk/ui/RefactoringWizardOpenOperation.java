package com.unitesk.requality.vscode.ui.ltk.ui;

import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.ltk.Refactoring;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation}: checks the initial conditions of a refactoring,
 * then opens its wizard dialog. A fatal error of the initial conditions is shown as a message and no dialog is opened.
 */
public class RefactoringWizardOpenOperation {
  /** {@code RefactoringWizardOpenOperation.INITIAL_CONDITION_CHECKING_FAILED}. */
  public static final int INITIAL_CONDITION_CHECKING_FAILED = IDialogConstants.CLIENT_ID + 1;

  private final RefactoringWizard wizard;
  private RefactoringStatus initialConditions;

  public RefactoringWizardOpenOperation(RefactoringWizard wizard) {
    this.wizard = wizard;
  }

  /** {@code RefactoringWizardOpenOperation#getInitialConditionCheckingStatus()}. */
  public RefactoringStatus getInitialConditionCheckingStatus() {
    return initialConditions;
  }

  /**
   * {@code RefactoringWizardOpenOperation#run(Shell, String)}: returns {@link #INITIAL_CONDITION_CHECKING_FAILED} when the
   * initial conditions fail, else the return code of the wizard dialog ({@link Window#OK} or {@link Window#CANCEL}).
   */
  public int run(Shell parent, String dialogTitle) throws InterruptedException {
    Refactoring refactoring = wizard.getRefactoring();
    try {
      initialConditions = refactoring.checkInitialConditions(new NullProgressMonitor());
    } catch (CoreException e) {
      ErrorDialog.openError(parent, dialogTitle, "An unexpected exception occurred during condition checking.",
          e.getStatus());
      return INITIAL_CONDITION_CHECKING_FAILED;
    } catch (OperationCanceledException e) {
      throw new InterruptedException(e.getMessage());
    }
    if (initialConditions.hasFatalError()) {
      MessageDialog.openError(parent, dialogTitle, initialConditions.getMessageMatchingSeverity(RefactoringStatus.FATAL));
      return INITIAL_CONDITION_CHECKING_FAILED;
    }
    wizard.setInitialConditionCheckingStatus(initialConditions);
    RefactoringWizardDialog2 dialog = new RefactoringWizardDialog2(parent, wizard);
    dialog.create();
    if (dialog.getCurrentPage() == null)
      return Window.CANCEL;
    return dialog.open();
  }
}
