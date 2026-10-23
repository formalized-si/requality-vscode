package com.unitesk.requality.vscode.ui.ltk.ui;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
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
 * {@code org.eclipse.ltk.internal.ui.refactoring.RefactoringStatusDialog}: hosts the status page ({@link ErrorWizardPage})
 * of the problems found. "Continue" performs the refactoring anyway, unless a fatal error blocks it.
 */
public class RefactoringStatusDialog extends Dialog {
  /** The return code of "Back" (the input page is shown again). */
  static final int BACK_RETURN = IDialogConstants.BACK_ID;

  private final ErrorWizardPage page;
  private final boolean showBack;
  private final boolean fatal;

  public RefactoringStatusDialog(Shell parentShell, ErrorWizardPage page, boolean showBack) {
    super(parentShell);
    this.page = page;
    this.showBack = showBack;
    this.fatal = page.getStatus().hasFatalError();
  }

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    newShell.setText("Found problems");
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout(1, false));
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    Label message = new Label(composite, SWT.WRAP);
    message.setText(fatal ? "A fatal error occurred while performing the refactoring."
        : "Review the information provided in the list below. Click 'Continue' to proceed.");
    message.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    page.createControl(composite);
    return composite;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    if (showBack)
      createButton(parent, IDialogConstants.BACK_ID, IDialogConstants.BACK_LABEL, false);
    createButton(parent, IDialogConstants.OK_ID, "Con&tinue", !fatal);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, fatal);
    Button ok = getButton(IDialogConstants.OK_ID);
    if (ok != null)
      ok.setEnabled(!fatal);
  }

  @Override
  protected void buttonPressed(int buttonId) {
    if (buttonId == IDialogConstants.BACK_ID) {
      setReturnCode(BACK_RETURN);
      close();
    } else {
      super.buttonPressed(buttonId);
    }
  }
}
