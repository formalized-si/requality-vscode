package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.ide.undo.CreateFolderOperation;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;

/**
 * {@code org.eclipse.ui.dialogs.WizardNewFolderMainPage}: the page of the new folder wizard. It asks for the parent
 * folder (the container group) and the name of the folder, and creates the folder with the undoable
 * {@link CreateFolderOperation} when the wizard finishes.
 */
public class WizardNewFolderMainPage extends WizardPage implements Listener {
  private static final int SIZING_CONTAINER_GROUP_HEIGHT = 250;

  private final IStructuredSelection currentSelection;
  private IFolder newFolder;
  private ResourceAndContainerGroup resourceGroup;

  /**
   * @param pageName the title of the page
   * @param selection the selection the container is initialized from
   */
  public WizardNewFolderMainPage(String pageName, IStructuredSelection selection) {
    super("newFolderPage1");
    setTitle(pageName);
    setDescription("Create a new folder resource.");
    this.currentSelection = selection;
  }

  // VSCODE: the advanced part (linked and virtual folders, the Advanced button) needs the linked resources of Eclipse.
  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));
    resourceGroup = new ResourceAndContainerGroup(composite, this, "Folder name:", "folder", false,
        SIZING_CONTAINER_GROUP_HEIGHT);
    resourceGroup.setAllowExistingResources(false);
    initializePage();
    validatePage();
    setErrorMessage(null);
    setMessage(null);
    setControl(composite);
  }

  /** The container is the selected resource (its container for a file), the folder name starts empty. */
  protected void initializePage() {
    if (currentSelection != null && !currentSelection.isEmpty()) {
      Object selectedElement = currentSelection.getFirstElement();
      if (selectedElement instanceof IResource) {
        IResource selectedResource = (IResource) selectedElement;
        IContainer container = selectedResource instanceof IContainer ? (IContainer) selectedResource
            : selectedResource.getParent();
        if (container != null)
          resourceGroup.setContainerFullPath(container.getFullPath());
      }
    }
    resourceGroup.setResource("");
  }

  @Override
  public void handleEvent(Event event) {
    setPageComplete(validatePage());
  }

  /** Validates the container and the name; sets the error message of the first problem. */
  protected boolean validatePage() {
    String message = resourceGroup.validateContainer("No folder specified.",
        "The specified project does not exist.");
    if (message == null) {
      message = resourceGroup.validateResource("The '" + resourceGroup.getResourceLabel() + "' name is empty.",
          "'" + resourceGroup.getResource() + "' is not a valid file name.",
          "'" + resourceGroup.getResource() + "' already exists.");
    }
    setErrorMessage(message);
    return message == null;
  }

  /**
   * Creates the folder with its missing parent folders in the workspace (an undoable operation of the workbench), and
   * returns it; null when the creation failed.
   */
  public IFolder createNewFolder() {
    if (newFolder != null)
      return newFolder;
    IPath containerPath = resourceGroup.getContainerFullPath();
    IPath newFolderPath = containerPath.append(resourceGroup.getResource());
    final IFolder newFolderHandle = ResourcesPlugin.getWorkspace().getRoot().getFolder(newFolderPath);
    CreateFolderOperation op = new CreateFolderOperation(newFolderHandle, "New Folder");
    try {
      WorkspaceUndoUtil.execute(op, new NullProgressMonitor(), getShell());
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), "Creation Problems", null, e.getStatus());
      return null;
    }
    newFolder = newFolderHandle;
    return newFolder;
  }
}
