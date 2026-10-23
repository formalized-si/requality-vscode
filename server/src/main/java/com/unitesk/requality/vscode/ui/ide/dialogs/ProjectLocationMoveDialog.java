package com.unitesk.requality.vscode.ui.ide.dialogs;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.dialogs.ProjectLocationMoveDialog} (the "Move Project" dialog): the new location of the
 * project; the result is {location URI, or null for the default location}.
 */
public class ProjectLocationMoveDialog extends Dialog {
  private final IProject project;
  private String title = "Move Project";
  private ProjectContentsLocationArea locationArea;
  private Label statusLine;
  private Object[] result;

  public ProjectLocationMoveDialog(Shell parentShell, IProject existingProject) {
    super(parentShell);
    this.project = existingProject;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText(title);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);
    Label message = new Label(composite, SWT.WRAP);
    message.setText("Enter the new location for project '"
        + com.unitesk.requality.vscode.ui.resources.ProjectRef.displayName(project) + "'.");
    message.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    locationArea = new ProjectContentsLocationArea(msg -> applyValidationResult(), composite, project);
    statusLine = new Label(composite, SWT.WRAP);
    statusLine.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    return composite;
  }

  @Override
  public void create() {
    super.create();
    applyValidationResult();
  }

  private String checkValid() {
    return locationArea.checkValidLocation();
  }

  private void applyValidationResult() {
    if (statusLine == null || locationArea == null)
      return;
    String error = checkValid();
    statusLine.setText(error == null ? "" : error);
    if (getButton(IDialogConstants.OK_ID) != null)
      getButton(IDialogConstants.OK_ID).setEnabled(error == null);
  }

  @Override
  protected void okPressed() {
    result = new Object[] { locationArea.getProjectLocationURI() };
    super.okPressed();
  }

  /** {location URI (null: the default location)}, or null when canceled. */
  public Object[] getResult() {
    return result;
  }
}
