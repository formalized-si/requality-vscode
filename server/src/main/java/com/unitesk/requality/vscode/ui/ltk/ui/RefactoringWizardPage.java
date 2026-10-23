package com.unitesk.requality.vscode.ui.ltk.ui;

import com.unitesk.requality.vscode.ui.jface.dialogs.IMessageProvider;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.ltk.Refactoring;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;

/** {@code org.eclipse.ltk.ui.refactoring.RefactoringWizardPage}: a page of a {@link RefactoringWizard}. */
public abstract class RefactoringWizardPage extends WizardPage {

  protected RefactoringWizardPage(String name) {
    super(name);
  }

  /** {@code RefactoringWizardPage#getRefactoringWizard()}. */
  protected final RefactoringWizard getRefactoringWizard() {
    return (RefactoringWizard) getWizard();
  }

  /** {@code RefactoringWizardPage#getRefactoring()}. */
  protected final Refactoring getRefactoring() {
    return getRefactoringWizard().getRefactoring();
  }

  /**
   * {@code RefactoringWizardPage#performFinish()}: the wizard finishes with the input of this page (a page that has
   * input overrides this to store it first).
   */
  protected boolean performFinish() {
    return getRefactoringWizard().finishRefactoring();
  }

  /**
   * {@code RefactoringWizardPage#setPageComplete(RefactoringStatus)}: the page is complete without errors; the most
   * severe message of the status is shown.
   */
  protected final void setPageComplete(RefactoringStatus status) {
    int severity = status.getSeverity();
    if (severity == RefactoringStatus.OK) {
      setErrorMessage(null);
      setMessage(null);
    } else if (severity >= RefactoringStatus.ERROR) {
      setErrorMessage(status.getMessageMatchingSeverity(severity));
      setMessage(null);
    } else {
      setErrorMessage(null);
      setMessage(status.getMessageMatchingSeverity(severity), IMessageProvider.WARNING);
    }
    setPageComplete(!status.hasError());
  }
}
