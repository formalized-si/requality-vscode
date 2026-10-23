package com.unitesk.requality.vscode.ui.navigator.resources;

import java.util.ArrayList;

import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.util.LocalSelectionTransfer;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.CommonDropAdapter;
import com.unitesk.requality.vscode.ui.navigator.CommonDropAdapterAssistant;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourceAttributes;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.MultiStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.dnd.DND;
import com.unitesk.requality.vscode.ui.swt.dnd.DropTargetEvent;
import com.unitesk.requality.vscode.ui.swt.dnd.FileTransfer;
import com.unitesk.requality.vscode.ui.swt.dnd.TransferData;
import com.unitesk.requality.vscode.ui.workbench.actions.CopyFilesAndFoldersOperation;

/**
 * {@code org.eclipse.ui.navigator.resources.ResourceDropAdapterAssistant}: projects, folders and files dragged in the
 * navigator are moved (or copied) into the folder or project they are dropped on (a file target means its folder);
 * files of the file system are copied into it.
 */
public class ResourceDropAdapterAssistant extends CommonDropAdapterAssistant {
  private static final String PLUGIN_ID = "org.eclipse.ui.navigator.resources";
  private static final IResource[] NO_RESOURCES = new IResource[0];

  @Override
  public boolean isSupportedType(TransferData aTransferType) {
    return super.isSupportedType(aTransferType) || FileTransfer.getInstance().isSupportedType(aTransferType);
  }

  @Override
  public IStatus validateDrop(Object target, int aDropOperation, TransferData transferType) {
    if (!(target instanceof IResource))
      return new Status(IStatus.INFO, PLUGIN_ID, "Target must be a resource");
    IResource resource = (IResource) target;
    if (!resource.isAccessible())
      return new Status(IStatus.ERROR, PLUGIN_ID, "Cannot drop a resource into closed project");
    IContainer destination = getActualTarget(resource);
    if (destination.getType() == IResource.ROOT)
      return new Status(IStatus.ERROR, PLUGIN_ID, "Resources cannot be siblings of projects");
    String message = null;
    if (LocalSelectionTransfer.getTransfer().isSupportedType(transferType)) {
      IResource[] selectedResources = getSelectedResources();
      boolean bProjectDrop = false;
      for (IResource res : selectedResources)
        if (res instanceof IProject) {
          bProjectDrop = true;
          break;
        }
      if (bProjectDrop)
        message = "Cannot drop a project into another project";
      else if (selectedResources.length == 0)
        message = "An error occurred during the drop operation.";
      else
        message = operation(aDropOperation).validateDestination(destination, selectedResources);
    }
    // VSCODE: files of the file system are dropped by the extension (explorer/dropFiles), not as a FileTransfer.
    if (message != null)
      return new Status(IStatus.ERROR, PLUGIN_ID, message);
    return Status.OK_STATUS;
  }

  /** The copy operation for a copy (or link) drop, else the move operation. */
  private CopyFilesAndFoldersOperation operation(int dropOperation) {
    return dropOperation == DND.DROP_COPY || dropOperation == DND.DROP_LINK ? new CopyFilesAndFoldersOperation(getShell())
        : CopyFilesAndFoldersOperation.move(getShell());
  }

  @Override
  public IStatus handleDrop(CommonDropAdapter aDropAdapter, DropTargetEvent aDropTargetEvent, Object aTarget) {
    if (aTarget == null || aDropTargetEvent.data == null)
      return Status.CANCEL_STATUS;
    IStatus status = null;
    IResource[] resources = null;
    TransferData currentTransfer = aDropAdapter.getCurrentTransfer();
    if (LocalSelectionTransfer.getTransfer().isSupportedType(currentTransfer))
      resources = getSelectedResources();
    if (resources != null && resources.length > 0) {
      if (aDropAdapter.getCurrentOperation() == DND.DROP_COPY || aDropAdapter.getCurrentOperation() == DND.DROP_LINK)
        status = performResourceCopy(aDropAdapter, resources);
      else
        status = performResourceMove(aDropAdapter, resources);
    }
    openError(status);
    IContainer target = getActualTarget((IResource) aTarget);
    if (target != null && target.isAccessible()) {
      try {
        target.refreshLocal(IResource.DEPTH_ONE, null);
      } catch (CoreException e) {
        // the explorer refreshes on the next change
      }
    }
    return status;
  }

  private IContainer getActualTarget(IResource mouseTarget) {
    // if the cursor is on a file, the target is its folder
    if (mouseTarget.getType() == IResource.FILE)
      return mouseTarget.getParent();
    return (IContainer) mouseTarget;
  }

  private IResource[] getSelectedResources() {
    ISelection selection = LocalSelectionTransfer.getTransfer().getSelection();
    if (selection instanceof IStructuredSelection)
      return getSelectedResources((IStructuredSelection) selection);
    return NO_RESOURCES;
  }

  private IResource[] getSelectedResources(IStructuredSelection selection) {
    ArrayList<IResource> selectedResources = new ArrayList<>();
    for (Object o : selection.toList()) {
      IResource resource = o instanceof IResource ? (IResource) o : null;
      if (resource != null)
        selectedResources.add(resource);
    }
    return selectedResources.toArray(new IResource[0]);
  }

  private IStatus performResourceCopy(CommonDropAdapter dropAdapter, IResource[] sources) {
    MultiStatus problems = new MultiStatus(PLUGIN_ID, 1, "Problems occurred while moving resources.", null);
    mergeStatus(problems, validateTarget(dropAdapter.getCurrentTarget(), dropAdapter.getCurrentTransfer(),
        dropAdapter.getCurrentOperation()));
    IContainer target = getActualTarget((IResource) dropAdapter.getCurrentTarget());
    // VSCODE: there are no linked resources or virtual folders, so the import type is always a copy (no
    // ImportTypeDialog).
    new CopyFilesAndFoldersOperation(getShell()).copyResources(sources, target);
    return problems;
  }

  private IStatus performResourceMove(CommonDropAdapter dropAdapter, IResource[] sources) {
    MultiStatus problems = new MultiStatus(PLUGIN_ID, 1, "Problems occurred while moving resources.", null);
    mergeStatus(problems, validateTarget(dropAdapter.getCurrentTarget(), dropAdapter.getCurrentTransfer(),
        dropAdapter.getCurrentOperation()));
    IContainer target = getActualTarget((IResource) dropAdapter.getCurrentTarget());
    sources = checkReadOnlyResources(sources);
    if (sources.length == 0)
      return Status.CANCEL_STATUS;
    // VSCODE: Eclipse runs the LTK move refactoring (MoveResourcesDescriptor) here; its effect and its undo are the
    // move of the resources by the MoveFilesAndFoldersOperation (MoveResourcesOperation in the operation history).
    CopyFilesAndFoldersOperation.move(getShell()).copyResources(sources, target);
    return problems;
  }

  /**
   * {@code ReadOnlyStateChecker.checkReadOnlyResources} ("Check Move"): read-only resources are moved only when the
   * user agrees.
   */
  private IResource[] checkReadOnlyResources(IResource[] sources) {
    ArrayList<IResource> r = new ArrayList<>();
    boolean yesToAll = false;
    for (IResource s : sources) {
      ResourceAttributes a = s.getResourceAttributes();
      if (yesToAll || a == null || !a.isReadOnly()) {
        r.add(s);
        continue;
      }
      MessageDialog d = new MessageDialog(getShell(), "Check Move", null,
          "'" + s.getName() + "' is read-only. Do you still wish to move it?", MessageDialog.QUESTION,
          new String[] { "Yes", "Yes to All", "No", "Cancel" }, 0);
      int answer = d.open();
      if (answer == 0)
        r.add(s);
      else if (answer == 1) {
        yesToAll = true;
        r.add(s);
      } else if (answer == 3 || answer < 0)
        return NO_RESOURCES;
    }
    return r.toArray(new IResource[0]);
  }

  private IStatus validateTarget(Object target, TransferData transferType, int dropOperation) {
    if (!(target instanceof IResource))
      return new Status(IStatus.INFO, PLUGIN_ID, "Target must be a resource");
    IResource resource = (IResource) target;
    if (!resource.isAccessible())
      return new Status(IStatus.ERROR, PLUGIN_ID, "Cannot drop a resource into closed project");
    IContainer destination = getActualTarget(resource);
    if (destination.getType() == IResource.ROOT)
      return new Status(IStatus.ERROR, PLUGIN_ID, "Resources cannot be siblings of projects");
    String message = null;
    if (LocalSelectionTransfer.getTransfer().isSupportedType(transferType)) {
      IResource[] selectedResources = getSelectedResources();
      if (selectedResources.length == 0)
        message = "An error occurred during the drop operation.";
      else
        message = operation(dropOperation).validateDestination(destination, selectedResources);
    }
    if (message != null)
      return new Status(IStatus.ERROR, PLUGIN_ID, message);
    return Status.OK_STATUS;
  }

  private void mergeStatus(MultiStatus status, IStatus toMerge) {
    if (!toMerge.isOK())
      status.merge(toMerge);
  }

  private void openError(IStatus status) {
    if (status == null)
      return;
    String genericTitle = "Drag and Drop Problem";
    int codes = IStatus.ERROR | IStatus.WARNING;
    if (!status.isMultiStatus()) {
      if ((status.getSeverity() & codes) != 0)
        ErrorDialog.openError(getShell(), genericTitle, null, status, codes);
      return;
    }
    IStatus[] children = status.getChildren();
    if (children.length == 0)
      return;
    if (children.length == 1) {
      ErrorDialog.openError(getShell(), status.getMessage(), null, children[0], codes);
      return;
    }
    ErrorDialog.openError(getShell(), genericTitle, null, status, codes);
  }
}
