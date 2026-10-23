package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.io.InputStream;

import com.unitesk.requality.vscode.ui.ide.undo.CreateFileOperation;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ui.dialogs.WizardNewFileCreationPage}: a wizard page that asks for the parent folder (a text
 * field with a Browse button) and the new file name, validates them against the workspace and creates the file
 * with the contents returned by {@link #getInitialContents()}.
 */
public abstract class WizardNewFileCreationPage extends WizardPage implements Listener {
  private static final String CONTAINER_EMPTY = "Enter or select a parent folder.";
  private static final String FILE_EMPTY = "Enter or select a file name.";
  private static final String CONTAINER_NOT_EXIST = "Folder \"%s\" does not exist.";
  private static final String CONTAINER_NOT_CONTAINER = "\"%s\" is not a folder.";
  private static final String FILE_EXISTS = "The file already exists and will be overwritten.";

  private Text containerNameField;
  private Text fileNameField;
  private Button browseButton;
  private final IStructuredSelection currentSelection;
  private IPath containerFullPath;
  private String fileExtension;
  private IFile newFileHandle;

  protected WizardNewFileCreationPage(String pageName, IStructuredSelection selection) {
    super(pageName);
    setPageComplete(false);
    currentSelection = selection;
    setTitle("New File");
    setDescription("Create a new file resource.");
  }

  /** The initial contents of the new file. */
  protected abstract InputStream getInitialContents();

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NULL);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    createContainerControls(composite);
    createFileControls(composite);
    initialPopulateContainerNameField();
    setErrorMessage(null);
    setMessage(null);
    setControl(composite);
    setPageComplete(validatePage());
  }

  private void createContainerControls(Composite parent) {
    Composite group = new Composite(parent, SWT.NULL);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    group.setLayout(layout);
    group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Label label = new Label(group, SWT.NONE);
    label.setText("Enter or select the parent folder:");
    label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    containerNameField = new Text(group, SWT.BORDER | SWT.SINGLE);
    containerNameField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    containerNameField.addListener(SWT.Modify, this);
    browseButton = new Button(group, SWT.PUSH);
    browseButton.setText("Browse...");
    browseButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        handleBrowseButtonPressed();
      }
    });
  }

  private void createFileControls(Composite parent) {
    Label label = new Label(parent, SWT.NONE);
    label.setText("File name:");
    fileNameField = new Text(parent, SWT.BORDER | SWT.SINGLE);
    fileNameField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    fileNameField.addListener(SWT.Modify, this);
  }

  private void initialPopulateContainerNameField() {
    if (currentSelection != null && !currentSelection.isEmpty()) {
      Object selectedElement = currentSelection.getFirstElement();
      if (selectedElement instanceof IResource) {
        IResource selectedResource = (IResource) selectedElement;
        IContainer container =
            selectedResource instanceof IContainer ? (IContainer) selectedResource : selectedResource.getParent();
        if (container != null)
          containerNameField.setText(container.getFullPath().makeRelative().toString());
      }
    }
  }

  @Override
  public void handleEvent(Event event) {
    if (event.widget == browseButton)
      handleBrowseButtonPressed();
    setPageComplete(validatePage());
  }

  /** Opens the workspace folder chooser and copies the selected folder into the parent folder field. */
  protected void handleBrowseButtonPressed() {
    ContainerSelectionDialog dialog =
        new ContainerSelectionDialog(
            getShell(),
            ResourcesPlugin.getWorkspace().getRoot(),
            true,
            "Select the parent folder:");
    if (dialog.open() == Window.OK) {
      Object[] result = dialog.getResult();
      if (result != null && result.length == 1)
        containerNameField.setText(((IPath) result[0]).toString());
    }
  }

  /** Validates the parent folder and the file name; sets the error or warning message. */
  protected boolean validatePage() {
    String containerName = containerNameField.getText();
    if (containerName.isEmpty()) {
      setErrorMessage(CONTAINER_EMPTY);
      return false;
    }
    IPath path = new Path(containerName).makeAbsolute();
    IResource container = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
    if (container == null) {
      setErrorMessage(String.format(CONTAINER_NOT_EXIST, containerName));
      return false;
    }
    if (!(container instanceof IContainer)) {
      setErrorMessage(String.format(CONTAINER_NOT_CONTAINER, containerName));
      return false;
    }
    containerFullPath = container.getFullPath();

    String fileName = fileNameField.getText();
    if (fileName.isEmpty()) {
      setErrorMessage(FILE_EMPTY);
      return false;
    }
    IStatus nameStatus = ResourcesPlugin.getWorkspace().validateName(fileName, IResource.FILE);
    if (!nameStatus.isOK()) {
      setErrorMessage(nameStatus.getMessage());
      return false;
    }
    setErrorMessage(null);
    IFile file = getNewFileHandle();
    if (file.exists())
      setMessage(FILE_EXISTS, WARNING);
    else
      setMessage(null);
    return true;
  }

  /** The file name as typed by the user. */
  public String getFileName() {
    return fileNameField.getText();
  }

  public void setFileName(String value) {
    fileNameField.setText(value);
  }

  public String getFileExtension() {
    return fileExtension;
  }

  /** Sets the extension appended to the file name when the name does not already end with it. */
  public void setFileExtension(String extension) {
    fileExtension = extension;
  }

  /** The full path of the parent folder, or null if it has not been validated yet. */
  public IPath getContainerFullPath() {
    return containerFullPath;
  }

  /** The file the page creates: the parent folder plus the file name with the extension applied. */
  public IFile getNewFileHandle() {
    String name = getFileName();
    if (fileExtension != null && !fileExtension.isEmpty() && !name.endsWith("." + fileExtension))
      name = name + "." + fileExtension;
    IContainer container = (IContainer) ResourcesPlugin.getWorkspace().getRoot().findMember(containerFullPath);
    return container.getFile(new Path(name));
  }

  /** Creates the file with the initial contents (an undoable operation of the workbench) and returns it, or null. */
  public IFile createNewFile() {
    if (newFileHandle != null)
      return newFileHandle;
    IFile newFile = getNewFileHandle();
    CreateFileOperation op = new CreateFileOperation(newFile, getInitialContents(), "New File");
    try {
      WorkspaceUndoUtil.execute(op, new NullProgressMonitor(), getShell());
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), "Creation Problems", null, e.getStatus());
      return null;
    }
    newFileHandle = newFile;
    return newFile;
  }
}
