package com.unitesk.requality.vscode.ui.ltk.ui;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.ltk.Change;
import com.unitesk.requality.vscode.ui.ltk.Refactoring;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ltk.ui.refactoring.RefactoringWizard}: the wizard of a refactoring. Its user input pages come from
 * {@link #addUserInputPages()}; the preview page ({@link PreviewWizardPage}) follows them unless
 * {@link #NO_PREVIEW_PAGE} is given. Finishing checks the final conditions and performs the change; the problems found
 * are returned for the status page ({@link ErrorWizardPage}) of the dialog.
 */
public abstract class RefactoringWizard extends Wizard {
  /** {@code RefactoringWizard.NONE}. */
  public static final int NONE = 0;
  /** {@code RefactoringWizard.DIALOG_BASED_USER_INTERFACE}: the wizard is shown as a dialog (the Preview button). */
  public static final int DIALOG_BASED_USER_INTERFACE = 1 << 2;
  /** {@code RefactoringWizard.NO_PREVIEW_PAGE}. */
  public static final int NO_PREVIEW_PAGE = 1 << 4;

  private final Refactoring refactoring;
  private final int flags;
  private final List<IWizardPage> inputPages = new ArrayList<>();
  private String defaultPageTitle;
  private RefactoringStatus initialConditionCheckingStatus = new RefactoringStatus();
  private RefactoringStatus conditionCheckingStatus;
  private Change pendingChange;

  protected RefactoringWizard(Refactoring refactoring, int flags) {
    this.refactoring = refactoring;
    this.flags = flags;
  }

  /** {@code RefactoringWizard#getRefactoring()}. */
  public final Refactoring getRefactoring() {
    return refactoring;
  }

  /** {@code RefactoringWizard#getWizardFlags()}. */
  public final int getWizardFlags() {
    return flags;
  }

  /** {@code RefactoringWizard#setDefaultPageTitle(String)}. */
  public final void setDefaultPageTitle(String title) {
    this.defaultPageTitle = title;
  }

  /** {@code RefactoringWizard#getDefaultPageTitle()}. */
  public final String getDefaultPageTitle() {
    return defaultPageTitle;
  }

  /** {@code RefactoringWizard#setInitialConditionCheckingStatus(RefactoringStatus)}. */
  public final void setInitialConditionCheckingStatus(RefactoringStatus status) {
    this.initialConditionCheckingStatus = status;
  }

  /** {@code RefactoringWizard#getInitialConditionCheckingStatus()}. */
  public final RefactoringStatus getInitialConditionCheckingStatus() {
    return initialConditionCheckingStatus;
  }

  /** The problems of the last finish that the user has to confirm (the status page), or null. */
  public final RefactoringStatus getConditionCheckingStatus() {
    return conditionCheckingStatus;
  }

  /** {@code RefactoringWizard#addUserInputPages()}: the pages asking for the input of the refactoring. */
  protected abstract void addUserInputPages();

  @Override
  public final void addPages() {
    addUserInputPages();
    inputPages.addAll(List.of(getPages()));
    if ((flags & NO_PREVIEW_PAGE) == 0)
      addPage(new PreviewWizardPage());
  }

  /** Whether the wizard has the preview page (the Preview button). */
  public final boolean hasPreviewPage() {
    return getPage(PreviewWizardPage.PAGE_NAME) != null;
  }

  @Override
  public boolean canFinish() {
    for (IWizardPage p : inputPages)
      if (!p.isPageComplete())
        return false;
    return true;
  }

  /**
   * {@code RefactoringWizard#createPreviewChange}: the change the preview page shows. A failure of the change creation
   * is reported and no preview is shown.
   */
  public final Change createPreviewChange() {
    try {
      return refactoring.createChange(new NullProgressMonitor());
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), getWindowTitle(), "The preview cannot be created.", e.getStatus());
    } catch (OperationCanceledException e) {
      return null;
    }
    return null;
  }

  /**
   * {@code RefactoringWizard#computeUserInputSuccessorPage}: the final conditions are checked before the preview; their
   * problems are shown first ("Found problems": Continue goes on to the preview unless a problem is fatal). Returns
   * the change of the preview, or null to stay on the input page.
   */
  final Change computePreviewChange() {
    RefactoringStatus status = new RefactoringStatus();
    status.merge(initialConditionCheckingStatus);
    try {
      status.merge(refactoring.checkFinalConditions(new NullProgressMonitor()));
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), getWindowTitle(), "The preview cannot be created.", e.getStatus());
      return null;
    } catch (OperationCanceledException e) {
      return null;
    }
    // computeUserInputSuccessorPage: any problem is shown before the preview
    if (!status.isOK()) {
      RefactoringStatusDialog dialog = new RefactoringStatusDialog(getShell(), new ErrorWizardPage(status), true);
      if (dialog.open() != IDialogConstants.OK_ID || status.hasFatalError())
        return null;
    }
    return createPreviewChange();
  }

  /** {@code RefactoringWizard#performFinish()}: the current page takes the input, then the refactoring finishes. */
  @Override
  public boolean performFinish() {
    IWizardPage page = getContainer().getCurrentPage();
    if (page instanceof RefactoringWizardPage)
      return ((RefactoringWizardPage) page).performFinish();
    return finishRefactoring();
  }

  /**
   * {@code RefactoringWizard#internalPerformFinish}: checks the final conditions and creates the change. The problems
   * (errors and fatal errors) are kept for the status page and nothing is performed (false); otherwise the change is
   * performed.
   */
  boolean finishRefactoring() {
    RefactoringStatus status = new RefactoringStatus();
    status.merge(initialConditionCheckingStatus);
    try {
      IProgressMonitor pm = new NullProgressMonitor();
      status.merge(refactoring.checkFinalConditions(pm));
      pendingChange = refactoring.createChange(pm);
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), getWindowTitle(), "Problems occurred while performing the refactoring.",
          e.getStatus());
      return true;
    } catch (OperationCanceledException e) {
      return false;
    }
    // RefactoringCore.getConditionCheckingFailedSeverity(): problems from warnings up stop the finish
    if (status.getSeverity() >= RefactoringStatus.WARNING) {
      conditionCheckingStatus = status;
      return false;
    }
    conditionCheckingStatus = null;
    return performPendingChange();
  }

  /** {@code PreviewWizardPage#performFinish}: the change shown by the preview (with its enablement) is performed. */
  final boolean performChange(Change change) {
    pendingChange = change;
    return performPendingChange();
  }

  /** Performs the change the user has accepted on the status page (the problems were not fatal). */
  public final boolean performPendingChange() {
    conditionCheckingStatus = null;
    if (pendingChange == null)
      return true;
    Change change = pendingChange;
    pendingChange = null;
    try {
      change.perform(new NullProgressMonitor());
      return true;
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), getWindowTitle(), "Problems occurred while performing the refactoring.",
          e.getStatus());
      return true;
    } catch (OperationCanceledException e) {
      return false;
    } catch (RuntimeException e) {
      RLogger.logError(e);
      throw e;
    }
  }

  @Override
  public void dispose() {
    refactoring.dispose();
    super.dispose();
  }
}
