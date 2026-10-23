package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.io.File;
import java.net.URI;
import java.util.function.Consumer;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ProjectRef;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.DirectoryDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ui.internal.ide.dialogs.ProjectContentsLocationArea}: "Use default location", the location
 * field and its Browse... button. The default location of a project is in the workspace root folder under its name.
 */
public class ProjectContentsLocationArea {
  private final IProject existingProject;
  private final Consumer<String> errorReporter;
  private String projectName;
  /** The location of a copy (Copy Project), not of the project itself (Move Project). */
  private boolean copy;
  private final Button useDefaultsButton;
  private final Label locationLabel;
  private final Text locationPathField;
  private final Button browseButton;

  /** The area of a new project (New Project, Copy Project): the default location of its name is used first. */
  public ProjectContentsLocationArea(Consumer<String> errorReporter, Composite composite) {
    this(errorReporter, composite, null);
  }

  /** The area of an existing project (Move Project): its location, or its default location. */
  public ProjectContentsLocationArea(Consumer<String> errorReporter, Composite composite, IProject existingProject) {
    this.errorReporter = errorReporter;
    this.existingProject = existingProject;
    this.projectName = existingProject == null ? "" : ProjectRef.displayName(existingProject);
    boolean defaultEnabled = existingProject == null
        || existingProject instanceof ProjectRef && ((ProjectRef) existingProject).isAtDefaultLocation();

    Composite projectGroup = new Composite(composite, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 4;
    projectGroup.setLayout(layout);
    projectGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    useDefaultsButton = new Button(projectGroup, SWT.CHECK | SWT.RIGHT);
    useDefaultsButton.setText("Use &default location");
    useDefaultsButton.setSelection(defaultEnabled);
    GridData buttonData = new GridData();
    buttonData.horizontalSpan = 4;
    useDefaultsButton.setLayoutData(buttonData);

    locationLabel = new Label(projectGroup, SWT.NONE);
    locationLabel.setText("&Location:");
    locationPathField = new Text(projectGroup, SWT.BORDER);
    GridData data = new GridData(GridData.FILL_HORIZONTAL);
    data.horizontalSpan = 2;
    locationPathField.setLayoutData(data);
    browseButton = new Button(projectGroup, SWT.PUSH);
    browseButton.setText("B&rowse...");
    browseButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
        handleLocationBrowseButtonPressed();
      }
    });

    if (defaultEnabled)
      locationPathField.setText(getDefaultPathDisplayString());
    else
      locationPathField.setText(existingProject.getLocation() == null ? "" : existingProject.getLocation().toOSString());
    // a new project: the location is in the workspace until the user changes it
    if (existingProject == null)
      copy = true;
    setUserAreaEnabled(!defaultEnabled);
    useDefaultsButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        boolean useDefaults = useDefaultsButton.getSelection();
        if (useDefaults)
          locationPathField.setText(getDefaultPathDisplayString());
        setUserAreaEnabled(!useDefaults);
        report();
      }
    });
    locationPathField.addModifyListener(e -> report());
  }

  private void report() {
    errorReporter.accept(checkValidLocation());
  }

  private void setUserAreaEnabled(boolean enabled) {
    locationLabel.setEnabled(enabled);
    locationPathField.setEnabled(enabled);
    browseButton.setEnabled(enabled);
  }

  /** The default location of a project with the current name. */
  private String getDefaultPathDisplayString() {
    File f = ServerContext.server() == null ? null : ServerContext.server().workspace().defaultLocation(projectName);
    return f == null ? "" : f.getAbsolutePath();
  }

  private void handleLocationBrowseButtonPressed() {
    DirectoryDialog dialog = new DirectoryDialog(locationPathField.getShell());
    dialog.setMessage("Select the location directory.");
    String dirName = locationPathField.getText().trim();
    if (!dirName.isEmpty() && new File(dirName).exists())
      dialog.setFilterPath(dirName);
    String selectedDirectory = dialog.open();
    if (selectedDirectory != null)
      locationPathField.setText(selectedDirectory);
  }

  /** The location is that of a copy of the project. */
  public void setCopy(boolean copy) {
    this.copy = copy;
  }

  /** The project name changed (Copy Project): the default location follows it. */
  public void updateProjectName(String newName) {
    projectName = newName;
    if (isDefault())
      locationPathField.setText(getDefaultPathDisplayString());
  }

  public boolean isDefault() {
    return useDefaultsButton.getSelection();
  }

  /** The location typed in, as an absolute path. */
  public String getProjectLocation() {
    return isDefault() ? getDefaultPathDisplayString() : locationPathField.getText().trim();
  }

  /** The location of the project: null for the default location. */
  public URI getProjectLocationURI() {
    return isDefault() ? null : new File(locationPathField.getText().trim()).getAbsoluteFile().toURI();
  }

  /** An error message for the location, or null when it is valid. */
  public String checkValidLocation() {
    String location = getProjectLocation();
    if (location.isEmpty())
      return "Project contents directory must be specified.";
    File f = new File(location);
    if (!f.isAbsolute())
      return "Invalid location path";
    if (isCurrentLocation())
      return "Location is the current location";
    if (f.exists() && !f.isDirectory())
      return "Invalid location path";
    IStatus st = ResourcesPlugin.getWorkspace().validateProjectLocation(existingProject, Path.fromOSString(f.getAbsolutePath()));
    if (!st.isOK())
      return st.getMessage();
    // the projects of the workspace are known by their folder names
    com.unitesk.requality.vscode.core.Workspace.Project other = ServerContext.server() == null ? null
        : ServerContext.server().workspace().anyProject(f.getName());
    if (other != null && (copy || existingProject == null || existingProject.getLocation() == null
        || !other.dir.getAbsoluteFile().equals(existingProject.getLocation().toFile().getAbsoluteFile())))
      return "A project with the folder name '" + f.getName() + "' already exists in the workspace.";
    if (f.exists() && !copy && !isCurrentLocation())
      return "'" + f.getAbsolutePath() + "' already exists.";
    return null;
  }

  /** True when the location is where the existing project is now. */
  public boolean isCurrentLocation() {
    File cur = existingProject == null || existingProject.getLocation() == null ? null
        : existingProject.getLocation().toFile().getAbsoluteFile();
    return cur != null && cur.equals(new File(getProjectLocation()).getAbsoluteFile());
  }
}
