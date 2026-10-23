package com.unitesk.requality.vscode.ui.ltk.ui;

import java.lang.reflect.InvocationTargetException;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizardContainer;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ltk.internal.ui.refactoring.RefactoringWizardDialog2}: the dialog of a refactoring wizard with the
 * buttons "Previe&w >" (the preview page; "< Back" on it), "OK" and "Cancel". The problems found when finishing are shown
 * in the status dialog ({@link RefactoringStatusDialog}).
 */
public class RefactoringWizardDialog2 extends Dialog implements IWizardContainer {
  /** {@code RefactoringWizardDialog2.PREVIEW_ID}. */
  static final int PREVIEW_ID = IDialogConstants.CLIENT_ID + 1;

  private final RefactoringWizard wizard;
  private IWizardPage currentPage;
  private IWizardPage visiblePage;
  private Composite pageContainer;
  private Label messageLabel;

  public RefactoringWizardDialog2(Shell shell, RefactoringWizard wizard) {
    super(shell);
    this.wizard = wizard;
    wizard.setContainer(this);
    wizard.addPages();
  }

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    String title = wizard.getDefaultPageTitle();
    if (title == null)
      title = wizard.getWindowTitle();
    newShell.setText(title == null ? "" : title);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginWidth = 10;
    layout.marginHeight = 10;
    area.setLayout(layout);
    area.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    pageContainer = new Composite(area, SWT.NONE);
    pageContainer.setLayout(new GridLayout());
    pageContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    messageLabel = new Label(area, SWT.WRAP);
    messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    currentPage = wizard.getStartingPage();
    if (currentPage != null)
      showPage(currentPage);
    return area;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    if (wizard.hasPreviewPage())
      createButton(parent, PREVIEW_ID, "Previe&w >", false);
    createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    updateButtons();
  }

  @Override
  protected void buttonPressed(int buttonId) {
    if (buttonId == PREVIEW_ID) {
      if (isPreviewPageActive())
        backPressed();
      else
        nextOrPreviewPressed();
    } else {
      super.buttonPressed(buttonId);
    }
  }

  @Override
  protected void okPressed() {
    if (wizard.performFinish()) {
      setReturnCode(OK);
      close();
      return;
    }
    RefactoringStatus problems = wizard.getConditionCheckingStatus();
    if (problems == null)
      return;
    RefactoringStatusDialog status = new RefactoringStatusDialog(getShell(), new ErrorWizardPage(problems), false);
    int result = status.open();
    if (result == IDialogConstants.OK_ID && wizard.performPendingChange()) {
      setReturnCode(OK);
      close();
    } else if (result == IDialogConstants.CANCEL_ID) {
      cancelPressed();
    }
  }

  @Override
  protected void cancelPressed() {
    if (wizard.performCancel())
      super.cancelPressed();
  }

  private boolean isPreviewPageActive() {
    return currentPage != null && PreviewWizardPage.PAGE_NAME.equals(currentPage.getName());
  }

  private void backPressed() {
    IWizardPage previous = currentPage.getPreviousPage();
    if (previous != null)
      showPage(previous);
  }

  private void nextOrPreviewPressed() {
    IWizardPage next = currentPage.getNextPage();
    if (next != null) {
      next.setPreviousPage(currentPage);
      showPage(next);
    }
  }

  @Override
  public IWizardPage getCurrentPage() {
    return currentPage;
  }

  @Override
  public void showPage(IWizardPage page) {
    if (page == null)
      return;
    if (page.getControl() == null)
      page.createControl(pageContainer);
    if (visiblePage != null && visiblePage != page) {
      visiblePage.setVisible(false);
      hide(visiblePage.getControl());
    }
    currentPage = page;
    visiblePage = page;
    Control c = page.getControl();
    if (c.getLayoutData() instanceof GridData)
      ((GridData) c.getLayoutData()).exclude = false;
    else
      c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    page.setVisible(true);
    pageContainer.layout();
    updateTitleBar();
    updateMessage();
    updateButtons();
  }

  private static void hide(Control c) {
    if (c == null)
      return;
    if (c.getLayoutData() instanceof GridData)
      ((GridData) c.getLayoutData()).exclude = true;
    else
      c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
  }

  @Override
  public void updateButtons() {
    if (currentPage == null)
      return;
    boolean preview = isPreviewPageActive();
    Button previewButton = getButton(PREVIEW_ID);
    if (previewButton != null) {
      previewButton.setText(preview ? IDialogConstants.BACK_LABEL : "Previe&w >");
      previewButton.setEnabled(preview || currentPage.canFlipToNextPage());
    }
    Button ok = getButton(IDialogConstants.OK_ID);
    if (ok != null)
      ok.setEnabled(wizard.canFinish());
    if (ok != null && getShell() != null)
      getShell().setDefaultButton(ok);
  }

  @Override
  public void updateMessage() {
    if (messageLabel == null || currentPage == null)
      return;
    String msg = currentPage.getErrorMessage();
    if (msg == null || msg.isEmpty())
      msg = currentPage.getMessage();
    if (msg == null || msg.isEmpty())
      msg = currentPage.getDescription();
    messageLabel.setText(msg == null ? "" : msg);
  }

  @Override
  public void updateTitleBar() {
  }

  @Override
  public void updateWindowTitle() {
    if (getShell() != null)
      getShell().setText(wizard.getWindowTitle() == null ? "" : wizard.getWindowTitle());
  }

  @Override
  public void run(boolean fork, boolean cancelable, IRunnableWithProgress runnable)
      throws InvocationTargetException, InterruptedException {
    new ProgressMonitorDialog(getShell()).run(fork, cancelable, runnable);
  }
}
