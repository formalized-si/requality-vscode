package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTreeViewer;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
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
 * {@code org.eclipse.ui.internal.wizards.datatransfer.WizardFileSystemResourceExportPage1}: exports the checked resources of
 * the workspace to a directory of the local file system.
 */
public class WizardFileSystemResourceExportPage1 extends WizardPage implements IOverwriteQuery {
  private CheckboxTreeViewer resourceTree;
  private Text destinationField;
  private Button createDirectoryStructureButton;
  private Button createSelectedDirectoriesButton;
  private Button overwriteExistingButton;
  private final List<IResource> initial;

  /** @param initial the selected resources, checked when the page opens */
  public WizardFileSystemResourceExportPage1(String name, List<IResource> initial) {
    super(name);
    this.initial = initial;
    setTitle("File System");
    setDescription("Export resources to the local file system.");
    setPageComplete(false);
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    setControl(composite);

    Label label = new Label(composite, SWT.NONE);
    label.setText("Select the resources to export:");
    resourceTree = ExportResourceTree.create(composite);
    resourceTree.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
    resourceTree.addCheckStateListener(event -> updatePageCompletion());
    if (initial != null)
      resourceTree.setCheckedElements(initial.toArray());

    Composite buttons = new Composite(composite, SWT.NONE);
    buttons.setLayout(new GridLayout(2, true));
    Button selectAll = new Button(buttons, SWT.PUSH);
    selectAll.setText("&Select All");
    selectAll.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        ExportResourceTree.checkAll(resourceTree, true);
        updatePageCompletion();
      }
    });
    Button deselectAll = new Button(buttons, SWT.PUSH);
    deselectAll.setText("&Deselect All");
    deselectAll.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        ExportResourceTree.checkAll(resourceTree, false);
        updatePageCompletion();
      }
    });

    Composite destination = new Composite(composite, SWT.NONE);
    destination.setLayout(new GridLayout(3, false));
    destination.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Label to = new Label(destination, SWT.NONE);
    to.setText("To director&y:");
    destinationField = new Text(destination, SWT.BORDER);
    destinationField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    destinationField.addModifyListener(e -> updatePageCompletion());
    Button browse = new Button(destination, SWT.PUSH);
    browse.setText("Bro&wse...");
    browse.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        DirectoryDialog dialog = new DirectoryDialog(getShell());
        dialog.setMessage("Select a directory to export to.");
        String path = dialog.open();
        if (path != null)
          destinationField.setText(path);
      }
    });

    createDirectoryStructureButton = new Button(composite, SWT.RADIO);
    createDirectoryStructureButton.setText("&Create directory structure for files");
    createDirectoryStructureButton.setSelection(true);
    createSelectedDirectoriesButton = new Button(composite, SWT.RADIO);
    createSelectedDirectoriesButton.setText("Create on&ly selected directories");
    overwriteExistingButton = new Button(composite, SWT.CHECK);
    overwriteExistingButton.setText("&Overwrite existing files without warning");
    updatePageCompletion();
  }

  /** The checked resources that are exported (the checked folders with their members). */
  List<IResource> resourcesToExport() {
    return resourceTree == null ? new java.util.ArrayList<>() : ExportResourceTree.topmostChecked(resourceTree);
  }

  void updatePageCompletion() {
    setPageComplete(!resourcesToExport().isEmpty()
        && destinationField != null && !destinationField.getText().trim().isEmpty());
  }

  /** Exports the checked resources to the directory; returns true if the wizard may close. */
  public boolean finish() {
    List<IResource> resources = resourcesToExport();
    if (resources.isEmpty()) {
      MessageDialog.openInformation(getShell(), "Information", "There are no resources currently selected for export.");
      return false;
    }
    File dir = new File(destinationField.getText().trim());
    if (dir.exists() && !dir.isDirectory()) {
      MessageDialog.openError(getShell(), "Export", "Target directory already exists as a file.");
      return false;
    }
    if (!dir.exists() && !MessageDialog.openQuestion(getShell(), "Export",
        "Target directory does not exist. Would you like to create it?"))
      return false;
    FileSystemExportOperation op = new FileSystemExportOperation(resources, dir,
        createDirectoryStructureButton.getSelection(), this);
    op.setOverwriteResources(overwriteExistingButton.getSelection());
    try {
      new ProgressMonitorDialog(getShell()).run(true, true, op);
    } catch (InvocationTargetException e) {
      MessageDialog.openError(getShell(), "Import", String.valueOf(e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
      return false;
    } catch (InterruptedException e) {
      return false;
    }
    if (!op.getProblems().isEmpty()) {
      MessageDialog.openError(getShell(), "Export Problems", String.join("\n", op.getProblems()));
      return false;
    }
    return true;
  }

  /** The answer for an existing file: the overwrite option, or the question for the file. */
  @Override
  public String queryOverwrite(String pathString) {
    if (overwriteExistingButton.getSelection())
      return IOverwriteQuery.ALL;
    boolean yes = MessageDialog.openQuestion(getShell(), "Question",
        pathString + " already exists. Would you like to overwrite it?");
    return yes ? IOverwriteQuery.YES : IOverwriteQuery.NO;
  }
}
