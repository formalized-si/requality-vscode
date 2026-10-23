package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTreeViewer;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.FileDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.WizardArchiveFileResourceExportPage1}: exports the checked resources of
 * the workspace into a zip (or gzipped tar) archive file.
 */
public class WizardArchiveFileResourceExportPage1 extends WizardPage {
  private CheckboxTreeViewer resourceTree;
  private Text destinationField;
  private Button compressButton;
  private Button createDirectoryStructureButton;
  private Button createSelectedDirectoriesButton;
  private final List<IResource> initial;

  /** @param initial the selected resources, checked when the page opens */
  public WizardArchiveFileResourceExportPage1(String name, List<IResource> initial) {
    super(name);
    this.initial = initial;
    setTitle("Zip file");
    setDescription("Export resources to an archive file on the local file system.");
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
    to.setText("To &zip file:");
    destinationField = new Text(destination, SWT.BORDER);
    destinationField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    destinationField.addModifyListener(e -> updatePageCompletion());
    Button browse = new Button(destination, SWT.PUSH);
    browse.setText("Bro&wse...");
    browse.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        FileDialog dialog = new FileDialog(getShell(), SWT.SAVE);
        dialog.setFilterExtensions(new String[] { "*.zip", "*.tar.gz", "*.*" });
        String path = dialog.open();
        if (path != null)
          destinationField.setText(path);
      }
    });

    compressButton = new Button(composite, SWT.CHECK);
    compressButton.setText("Co&mpress the contents of the file");
    compressButton.setSelection(true);
    createDirectoryStructureButton = new Button(composite, SWT.RADIO);
    createDirectoryStructureButton.setText("&Create directory structure for files");
    createDirectoryStructureButton.setSelection(true);
    createSelectedDirectoriesButton = new Button(composite, SWT.RADIO);
    createSelectedDirectoriesButton.setText("Create on&ly selected directories");
    updatePageCompletion();
  }

  List<IResource> resourcesToExport() {
    return resourceTree == null ? new java.util.ArrayList<>() : ExportResourceTree.topmostChecked(resourceTree);
  }

  void updatePageCompletion() {
    setPageComplete(!resourcesToExport().isEmpty()
        && destinationField != null && !destinationField.getText().trim().isEmpty());
  }

  /** Writes the archive of the checked resources; returns true if the wizard may close. */
  public boolean finish() {
    List<IResource> resources = resourcesToExport();
    if (resources.isEmpty()) {
      MessageDialog.openInformation(getShell(), "Information", "There are no resources currently selected for export.");
      return false;
    }
    File file = new File(destinationField.getText().trim());
    if (file.isDirectory()) {
      MessageDialog.openError(getShell(), "Export", "Export destination must be a file, not a directory.");
      return false;
    }
    if (file.exists() && !MessageDialog.openQuestion(getShell(), "Export",
        "Target file already exists. Would you like to overwrite it?"))
      return false;
    ArchiveFileExportOperation op = new ArchiveFileExportOperation(resources, file,
        createDirectoryStructureButton.getSelection(), compressButton.getSelection());
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
}
