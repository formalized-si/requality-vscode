package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.net.URI;

import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.ui.ide.dialogs.ProjectContentsLocationArea;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;

/**
 * {@code org.eclipse.ui.dialogs.WizardNewProjectCreationPage}: the page of the new project wizard that asks for the
 * project name and its location ("Use default location", or the location chosen by the user).
 */
public class WizardNewProjectCreationPage extends WizardPage implements Listener {
  private static final String PROJECT_NAME_EMPTY = "Project name must be specified";
  private static final String PROJECT_EXISTS = "A project with that name already exists in the workspace.";

  private Text projectNameField;
  private ProjectContentsLocationArea locationArea;
  private WorkingSetGroup workingSetGroup;
  private String errorMessage;

  /** @param pageName the name of the page */
  public WizardNewProjectCreationPage(String pageName) {
    super(pageName);
    setPageComplete(false);
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NULL);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));

    Composite nameGroup = new Composite(composite, SWT.NONE);
    GridLayout nameLayout = new GridLayout();
    nameLayout.numColumns = 2;
    nameGroup.setLayout(nameLayout);
    nameGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Label label = new Label(nameGroup, SWT.NONE);
    label.setText("Project name:");
    projectNameField = new Text(nameGroup, SWT.BORDER);
    projectNameField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    projectNameField.addListener(SWT.Modify, this);

    locationArea = new ProjectContentsLocationArea(message -> errorMessage = message, composite);

    setErrorMessage(null);
    setMessage(null);
    setControl(composite);
    setPageComplete(validatePage());
  }

  /**
   * {@code WizardNewProjectCreationPage.createWorkingSetGroup}: creates the "Working sets" group of the page, once. The
   * group is created after {@link #createControl} (the composite is the control of the page).
   *
   * @param composite the composite of the page
   * @param selection the selection of the workbench, the working sets of its elements are selected
   * @param supportedWorkingSetTypes the ids of the working set types that can be chosen in the group
   * @return the working set group
   */
  public WorkingSetGroup createWorkingSetGroup(Composite composite, IStructuredSelection selection,
      String[] supportedWorkingSetTypes) {
    if (workingSetGroup != null) {
      return workingSetGroup;
    }
    workingSetGroup = new WorkingSetGroup(composite, selection, supportedWorkingSetTypes,
        Activator.getDefault().getDialogSettings());
    return workingSetGroup;
  }

  /**
   * The working sets selected by the user, if any: the page is not configured to interact with working sets when
   * {@link #createWorkingSetGroup} has not been called.
   *
   * @return the selected working sets
   */
  public IWorkingSet[] getSelectedWorkingSets() {
    return workingSetGroup == null ? new IWorkingSet[0] : workingSetGroup.getSelectedWorkingSets();
  }

  /** The name changed: the default location follows it, then the page is validated again. */
  @Override
  public void handleEvent(Event event) {
    if (locationArea != null)
      locationArea.updateProjectName(getProjectName());
    setPageComplete(validatePage());
  }

  /** Validates the name and the location; sets the error message of the first problem. */
  protected boolean validatePage() {
    errorMessage = null;
    String name = getProjectName();
    if (name.isEmpty()) {
      setErrorMessage(PROJECT_NAME_EMPTY);
      return false;
    }
    IStatus nameStatus = ResourcesPlugin.getWorkspace().validateName(name, IResource.PROJECT);
    if (!nameStatus.isOK()) {
      setErrorMessage(nameStatus.getMessage());
      return false;
    }
    if (ResourcesPlugin.getWorkspace().getRoot().getProject(name).exists()) {
      setErrorMessage(PROJECT_EXISTS);
      return false;
    }
    String locationMessage = locationArea == null ? null : locationArea.checkValidLocation();
    setErrorMessage(locationMessage != null ? locationMessage : errorMessage);
    return locationMessage == null && errorMessage == null;
  }

  /** The name of the project as typed by the user. */
  public String getProjectName() {
    return projectNameField == null ? "" : projectNameField.getText().trim();
  }

  /** The handle of the project named by the page. */
  public IProject getProjectHandle() {
    return ResourcesPlugin.getWorkspace().getRoot().getProject(getProjectName());
  }

  /** Whether the project is at the default location (the workspace location of the name). */
  public boolean useDefaults() {
    return locationArea.isDefault();
  }

  /** The location of the project when it is not at the default location, else null. */
  public URI getLocationURI() {
    return locationArea.getProjectLocationURI();
  }
}
