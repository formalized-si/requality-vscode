package com.unitesk.requality.vscode.ui.ide.dialogs;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.ProjectRef;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ui.dialogs.ProjectLocationSelectionDialog} (the "Copy Project" dialog): the name of the copy
 * ("Copy of ...") and its location; the result is {name, location URI or null for the default location}.
 */
public class ProjectLocationSelectionDialog extends Dialog {
  private final IProject project;
  private String title = "Project";
  private Text projectNameField;
  private ProjectContentsLocationArea locationArea;
  private Label statusLine;
  private Object[] result;

  public ProjectLocationSelectionDialog(Shell parentShell, IProject existingProject) {
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

  /** A project of the workspace has the name (as handle or in its description). */
  private static boolean exists(String name) {
    for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects())
      if (p.exists() && (name.equals(p.getName()) || name.equals(ProjectRef.displayName(p))))
        return true;
    return ResourcesPlugin.getWorkspace().getRoot().getProject(name).exists();
  }

  /** CopyProjectAction: "Copy of X", then "Copy (2) of X"... until the name is free. */
  private String getCopyNameFor(String projectName) {
    if (!exists(projectName))
      return projectName;
    int counter = 1;
    while (true) {
      String nameSegment = counter > 1 ? "Copy (" + counter + ") of " + projectName : "Copy of " + projectName;
      if (!exists(nameSegment))
        return nameSegment;
      counter++;
    }
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);
    Composite nameGroup = new Composite(composite, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    nameGroup.setLayout(layout);
    nameGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Label projectLabel = new Label(nameGroup, SWT.NONE);
    projectLabel.setText("&Project name:");
    projectNameField = new Text(nameGroup, SWT.BORDER);
    projectNameField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    projectNameField.setText(getCopyNameFor(ProjectRef.displayName(project)));
    // the location of the copy is that of a new project named as the copy (Eclipse: the area without a project)
    locationArea = new ProjectContentsLocationArea(msg -> applyValidationResult(), composite);
    locationArea.updateProjectName(projectNameField.getText());
    projectNameField.addModifyListener(e -> {
      locationArea.updateProjectName(projectNameField.getText());
      applyValidationResult();
    });
    statusLine = new Label(composite, SWT.WRAP);
    statusLine.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    return composite;
  }

  @Override
  public void create() {
    super.create();
    applyValidationResult();
  }

  /** The first problem of the name or the location, or null. */
  private String checkValid() {
    String name = projectNameField.getText();
    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    IStatus nameStatus = workspace.validateName(name, IResource.PROJECT);
    if (!nameStatus.isOK())
      return nameStatus.getMessage();
    if (exists(name))
      return "A project with that name already exists in the workspace.";
    String loc = locationArea.checkValidLocation();
    if (loc == null && new java.io.File(locationArea.getProjectLocation()).exists())
      return "'" + locationArea.getProjectLocation() + "' already exists.";
    return loc;
  }

  private void applyValidationResult() {
    if (statusLine == null || projectNameField == null)
      return;
    String error = checkValid();
    statusLine.setText(error == null ? "" : error);
    if (getButton(IDialogConstants.OK_ID) != null)
      getButton(IDialogConstants.OK_ID).setEnabled(error == null);
  }

  @Override
  protected void okPressed() {
    result = new Object[] { projectNameField.getText(), locationArea.getProjectLocationURI() };
    super.okPressed();
  }

  /** {name, location URI (null: the default location)}, or null when canceled. */
  public Object[] getResult() {
    return result;
  }
}
