package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.ide.dialogs.FileSystemElement;
import com.unitesk.requality.vscode.ui.ide.wizards.FileSystemStructureProvider;
import com.unitesk.requality.vscode.ui.ide.wizards.WizardFileSystemResourceImportPage1;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.WizardFileSystemResourceImportPage1} as the page of the File System
 * import wizard: the options "Overwrite existing resources without warning" and "Create top-level folder", and the
 * import of the selected files and folders with {@link ImportOperation}.
 */
public class FileSystemImportPage1 extends WizardFileSystemResourceImportPage1 implements IOverwriteQuery {
  private Button overwriteExistingResourcesCheckbox;
  private Button createTopLevelFolderCheckbox;

  public FileSystemImportPage1(String name, IWorkbench aWorkbench, IStructuredSelection selection) {
    super(name, aWorkbench, selection);
    setTitle("File System");
    setDescription("Import resources from the local file system into an existing project.");
  }

  /** With no file types chosen in the type filter, every file of the source is acceptable (and checked). */
  @Override
  protected boolean isExportableExtension(String extension) {
    return selectedTypes.isEmpty() || super.isExportableExtension(extension);
  }

  @Override
  protected void createOptionsGroupButtons(Group optionsGroup) {
    overwriteExistingResourcesCheckbox = new Button(optionsGroup, com.unitesk.requality.vscode.ui.swt.SWT.CHECK);
    overwriteExistingResourcesCheckbox.setFont(optionsGroup.getFont());
    overwriteExistingResourcesCheckbox.setText("Overwrite existing resources without warning");
    createTopLevelFolderCheckbox = new Button(optionsGroup, com.unitesk.requality.vscode.ui.swt.SWT.CHECK);
    createTopLevelFolderCheckbox.setFont(optionsGroup.getFont());
    createTopLevelFolderCheckbox.setText("Create top-level folder");
    createTopLevelFolderCheckbox.setSelection(false);
    createTopLevelFolderCheckbox.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateWidgetEnablements();
      }
    });
  }

  @Override
  protected boolean validateOptionsGroup() {
    return true;
  }

  /** Imports the checked files and folders of the source; returns true if the wizard may close. */
  @Override
  public boolean finish() {
    if (!ensureSourceIsValid())
      return false;
    saveWidgetValues();
    List<Object> fileSystemObjects = new ArrayList<>();
    for (Object o : getSelectedResources())
      fileSystemObjects.add(((FileSystemElement) o).getFileSystemObject());
    if (fileSystemObjects.isEmpty()) {
      MessageDialog.openInformation(getContainer().getShell(), "Information", "No resources selected");
      return false;
    }
    return importResources(fileSystemObjects);
  }

  /** Runs the import of the objects into the destination container. */
  protected boolean importResources(List<Object> fileSystemObjects) {
    File source = getSourceDirectory();
    IPath destination = getContainerFullPath();
    if (createTopLevelFolderCheckbox.getSelection())
      destination = destination.append(source.getName());
    ImportOperation op = new ImportOperation(destination, source, FileSystemStructureProvider.INSTANCE, this,
        fileSystemObjects);
    op.setOverwriteResources(overwriteExistingResourcesCheckbox.getSelection());
    try {
      new ProgressMonitorDialog(getShell()).run(true, true, op);
    } catch (InvocationTargetException e) {
      MessageDialog.openError(getShell(), "Import", String.valueOf(e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
      return false;
    } catch (InterruptedException e) {
      return false;
    }
    IStatus status = op.getStatus();
    if (status.getSeverity() != IStatus.OK) {
      MessageDialog.openError(getShell(), "Import", status.getMessage());
      return false;
    }
    return true;
  }

  /**
   * {@code org.eclipse.ui.dialogs.WizardDataTransferPage.queryOverwrite}: the answer of the import for an existing
   * file, asked with the buttons Yes, Yes to All, No, No to All and Cancel. The overwrite option of the page answers
   * {@link IOverwriteQuery#ALL} without asking.
   */
  @Override
  public String queryOverwrite(String pathString) {
    if (overwriteExistingResourcesCheckbox.getSelection())
      return IOverwriteQuery.ALL;
    Path path = Path.fromOSString(pathString);
    String messageString;
    // Break the message up if there is a file name and a directory and there are at least 2 segments.
    if (path.getFileExtension() == null || path.segmentCount() < 2) {
      messageString = pathString + " already exists.  Would you like to overwrite it?";
    } else {
      messageString = "Overwrite '" + path.lastSegment() + "' in folder '"
          + path.removeLastSegments(1).toOSString() + "'?";
    }
    String[] response = new String[] { IOverwriteQuery.YES, IOverwriteQuery.ALL, IOverwriteQuery.NO,
        IOverwriteQuery.NO_ALL, IOverwriteQuery.CANCEL };
    final MessageDialog dialog = new MessageDialog(getContainer().getShell(), "Question", null, messageString,
        MessageDialog.QUESTION, 0, IDialogConstants.YES_LABEL, IDialogConstants.YES_TO_ALL_LABEL,
        IDialogConstants.NO_LABEL, IDialogConstants.NO_TO_ALL_LABEL, IDialogConstants.CANCEL_LABEL);
    dialog.open();
    return dialog.getReturnCode() < 0 ? IOverwriteQuery.CANCEL : response[dialog.getReturnCode()];
  }
}
