package com.unitesk.requality.vscode.ui.ide.wizards;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.dialogs.ContainerSelectionDialog;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.WizardResourceImportPage}: the base of the import pages, with the
 * destination container group and the options group.
 */
public abstract class WizardResourceImportPage extends WizardPage {
  protected IWorkbench workbench;
  protected IStructuredSelection currentSelection;
  protected Text containerNameField;
  protected Button containerBrowseButton;
  private String initialContainerFieldValue = "";

  protected WizardResourceImportPage(String name, IWorkbench workbench, IStructuredSelection selection) {
    super(name);
    this.workbench = workbench;
    this.currentSelection = selection;
  }

  /** The destination container of the import, as a workspace path. */
  protected IPath getContainerFullPath() {
    return new Path(containerNameField == null ? initialContainerFieldValue : containerNameField.getText());
  }

  /** Sets the destination container field; before the controls exist the value is kept for createContainerGroup. */
  protected void setContainerFieldValue(String value) {
    if (containerNameField == null)
      initialContainerFieldValue = value;
    else
      containerNameField.setText(value);
  }

  /** Creates the "Into folder" group. */
  protected void createContainerGroup(Composite parent) {
    Composite container = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 3;
    container.setLayout(layout);
    container.setFont(parent.getFont());
    container.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));

    Label label = new Label(container, SWT.NONE);
    label.setText("Into folder:");
    label.setFont(parent.getFont());

    containerNameField = new Text(container, SWT.BORDER);
    GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL);
    containerNameField.setLayoutData(data);
    containerNameField.setFont(parent.getFont());
    containerNameField.setText(initialContainerFieldValue);

    containerBrowseButton = new Button(container, SWT.PUSH);
    containerBrowseButton.setText("Browse...");
    containerBrowseButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
    containerBrowseButton.setFont(parent.getFont());
    containerBrowseButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        handleContainerBrowseButtonPressed();
      }
    });
    containerNameField.addModifyListener(new com.unitesk.requality.vscode.ui.swt.events.ModifyListener() {
      @Override
      public void modifyText(com.unitesk.requality.vscode.ui.swt.events.ModifyEvent e) {
        updatePageCompletion();
      }
    });
  }

  /** Recomputes the completion of the page from the source, the destination and the options. */
  protected void updatePageCompletion() {
    setPageComplete(validateSourceGroup() && validateDestinationGroup() && validateOptionsGroup());
  }

  /** Opens the workspace folder chooser for the destination container. */
  protected void handleContainerBrowseButtonPressed() {
    ContainerSelectionDialog dialog = new ContainerSelectionDialog(getShell(),
        ResourcesPlugin.getWorkspace().getRoot(), false, "Select the destination folder");
    dialog.open();
    Object[] result = dialog.getResult();
    if (result != null && result.length > 0 && result[0] instanceof IPath)
      setContainerFieldValue(((IPath) result[0]).toString());
  }

  /** Creates the "Options" group, filled by {@link #createOptionsGroupButtons(Group)}. */
  protected void createOptionsGroup(Composite parent) {
    Group optionsGroup = new Group(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    optionsGroup.setLayout(layout);
    optionsGroup.setFont(parent.getFont());
    optionsGroup.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
    optionsGroup.setText("Options");
    createOptionsGroupButtons(optionsGroup);
  }

  /** Creates the option widgets of the page. */
  protected abstract void createOptionsGroupButtons(Group optionsGroup);

  /** Validates the option widgets; returns true if the page may be completed. */
  protected abstract boolean validateOptionsGroup();

  /** Validates the source group; returns true if the page may be completed. */
  protected abstract boolean validateSourceGroup();

  /** Validates the destination container; returns true if the page may be completed. */
  protected boolean validateDestinationGroup() {
    IPath path = getContainerFullPath();
    return path != null && path.segmentCount() > 0;
  }

  /** Performs the import; returns true if the wizard may close. */
  public abstract boolean finish();

  /** Sets the button layout data of a page button. */
  protected void setButtonLayoutData(Button button) {
    GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
    button.setLayoutData(data);
  }
}
