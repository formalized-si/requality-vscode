package com.unitesk.requality.vscode.ui.jface.dialogs;

import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.ui.dialogs.SelectionDialog}. */
public abstract class SelectionDialog extends Dialog {
  private Object[] result;
  private List<?> initialSelections = java.util.Collections.emptyList();
  private String title = "";
  private String message = "";

  protected SelectionDialog(Shell parentShell) {
    super(parentShell);
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText(title);
  }

  public void setTitle(String title) {
    this.title = title == null ? "" : title;
    if (getShell() != null)
      getShell().setText(this.title);
  }

  public String getTitle() {
    return title;
  }

  public void setMessage(String message) {
    this.message = message == null ? "" : message;
  }

  public String getMessage() {
    return message;
  }

  protected Label createMessageArea(Composite composite) {
    Label label = new Label(composite, SWT.WRAP);
    label.setText(message);
    label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    return label;
  }

  public void setInitialSelections(Object... selected) {
    initialSelections = Arrays.asList(selected);
  }

  public void setInitialElementSelections(List<?> selected) {
    initialSelections = selected;
  }

  protected List<?> getInitialElementSelections() {
    return initialSelections;
  }

  public Object[] getResult() {
    return result;
  }

  protected void setResult(List<?> newResult) {
    result = newResult == null ? null : newResult.toArray();
  }

  protected void setSelectionResult(Object[] newResult) {
    result = newResult;
  }
}
