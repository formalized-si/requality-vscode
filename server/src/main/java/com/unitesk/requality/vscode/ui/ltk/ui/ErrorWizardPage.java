package com.unitesk.requality.vscode.ui.ltk.ui;

import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;

/**
 * {@code org.eclipse.ltk.internal.ui.refactoring.ErrorWizardPage}: "Found problems", the list of the problems of a
 * refactoring ({@link RefactoringStatus} entries). It is shown in the status dialog of the refactoring wizard.
 */
public class ErrorWizardPage extends WizardPage {
  /** {@code IErrorWizardPage.PAGE_NAME}. */
  public static final String PAGE_NAME = "ErrorWizardPage";

  private final RefactoringStatus status;

  public ErrorWizardPage(RefactoringStatus status) {
    super(PAGE_NAME);
    this.status = status;
    setTitle("Found problems");
  }

  /** {@code ErrorWizardPage#getStatus()}. */
  public RefactoringStatus getStatus() {
    return status;
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout(1, false));
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    Table table = new Table(composite, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
    table.setHeaderVisible(false);
    table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    for (RefactoringStatus.Entry e : status.getEntries()) {
      TableItem item = new TableItem(table, SWT.NONE);
      item.setText(prefix(e.getSeverity()) + e.getMessage());
    }
    setControl(composite);
  }

  private static String prefix(int severity) {
    if (severity == RefactoringStatus.FATAL)
      return "Fatal error: ";
    if (severity == RefactoringStatus.ERROR)
      return "Error: ";
    if (severity == RefactoringStatus.WARNING)
      return "Warning: ";
    return "";
  }
}
