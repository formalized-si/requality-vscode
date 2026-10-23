package com.unitesk.requality.vscode.ui.ide.undo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.operations.IOperationHistory;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IContainerSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.ResourceSnapshotFactory;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.MultiStatus;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.ide.undo.WorkspaceUndoUtil}: the undo context of the workspace operations (the workbench undo
 * context, so that Edit > Undo and Redo revert them), the UI info adapter that carries the shell, and the resource
 * work the operations share (copy, move, delete and recreate with their snapshots).
 */
public final class WorkspaceUndoUtil {
  private WorkspaceUndoUtil() {
  }

  /** The workbench undo context: the operations of the workspace are undone by Edit > Undo. */
  public static IUndoContext getWorkspaceUndoContext() {
    return PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
  }

  /** The undo context of the problems, tasks and bookmarks: they match the workspace context. */
  // VSCODE: the marker contexts of Eclipse (tasks, bookmarks, problems) are the workspace context here.
  public static IUndoContext getMarkerUndoContext() {
    return getWorkspaceUndoContext();
  }

  /** The UI info of an operation: the adapter gives the shell of the message dialogs. */
  public static IAdaptable getUIInfoAdapter(final Shell shell) {
    return new IAdaptable() {
      @Override
      public <T> T getAdapter(Class<T> clazz) {
        if (clazz == Shell.class) {
          return clazz.cast(shell);
        }
        return null;
      }
    };
  }

  /** The shell of an operation's UI info, else the active window's shell. */
  public static Shell getShell(IAdaptable uiInfo) {
    Shell shell = uiInfo == null ? null : uiInfo.getAdapter(Shell.class);
    if (shell != null) {
      return shell;
    }
    return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
  }

  /**
   * Executes an operation in the operation history of the workbench (as Eclipse's IDE actions do), a failure of the
   * operation is thrown as its cause.
   */
  public static IStatus execute(IUndoableOperation operation, IProgressMonitor monitor, Shell shell)
      throws CoreException {
    try {
      // the status of the execution is computed first, so that the user is asked (the read-only resources to delete)
      if (operation instanceof AbstractWorkspaceOperation workspaceOperation) {
        IStatus status = workspaceOperation.computeExecutionStatus(monitor);
        if (!status.isOK()) {
          return status;
        }
      }
      return PlatformUI.getWorkbench().getOperationSupport().getOperationHistory().execute(operation, monitor,
          getUIInfoAdapter(shell));
    } catch (ExecutionException e) {
      if (e.getCause() instanceof CoreException)
        throw (CoreException) e.getCause();
      throw new CoreException(new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES, e.getMessage(), e));
    }
  }

  /**
   * Executes an operation without recording it in the operation history: the operation cannot be undone (the deletion
   * of the contents of projects, which Eclipse executes directly).
   */
  public static IStatus executeWithoutHistory(IUndoableOperation operation, IProgressMonitor monitor, Shell shell)
      throws CoreException {
    try {
      return operation.execute(monitor, getUIInfoAdapter(shell));
    } catch (ExecutionException e) {
      if (e.getCause() instanceof CoreException)
        throw (CoreException) e.getCause();
      throw new CoreException(new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES, e.getMessage(), e));
    }
  }

  /** The operation history of the workbench. */
  static IOperationHistory history() {
    return PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
  }

  static IWorkspace getWorkspace() {
    return ResourcesPlugin.getWorkspace();
  }

  static IWorkspaceRoot getWorkspaceRoot() {
    return getWorkspace().getRoot();
  }

  /**
   * Deletes the resources, recording their snapshots (for the undo). Resources that are nested in another are deleted
   * with it by the caller.
   */
  static IResourceSnapshot<? extends IResource>[] delete(IResource[] resourcesToDelete, IProgressMonitor mon,
      IAdaptable uiInfo, boolean deleteContent) throws CoreException {
    List<CoreException> exceptions = new ArrayList<>();
    @SuppressWarnings("unchecked")
    IResourceSnapshot<? extends IResource>[] returned = new IResourceSnapshot<?>[resourcesToDelete.length];
    for (int i = 0; i < resourcesToDelete.length; ++i) {
      try {
        returned[i] = delete(resourcesToDelete[i], mon, uiInfo, deleteContent);
      } catch (CoreException e) {
        exceptions.add(e);
      }
    }
    IStatus result = createResult(exceptions);
    if (!result.isOK()) {
      throw new CoreException(result);
    }
    return returned;
  }

  /** Deletes one resource (a project with or without its contents), returning its snapshot. */
  static IResourceSnapshot<? extends IResource> delete(IResource resourceToDelete, IProgressMonitor monitor,
      IAdaptable uiInfo, boolean deleteContent) throws CoreException {
    IResourceSnapshot<? extends IResource> resourceDescription = ResourceSnapshotFactory.fromResource(resourceToDelete);
    if (resourceToDelete.getType() == IResource.PROJECT) {
      IProject project = (IProject) resourceToDelete;
      project.delete(deleteContent, false, monitor);
    } else {
      resourceToDelete.delete(IResource.KEEP_HISTORY, monitor);
      resourceDescription.recordStateFromHistory(monitor);
    }
    return resourceDescription;
  }

  /**
   * Copies the resources to the destination; the resources overwritten at the destination are returned as snapshots
   * (for the undo), the copies are added to {@code resourcesAtDestination}.
   */
  static IResourceSnapshot<? extends IResource>[] copy(IResource[] resources, IPath destination,
      List<IResource> resourcesAtDestination, IProgressMonitor monitor, IAdaptable uiInfo, boolean pathIncludesName)
      throws CoreException {
    List<IResourceSnapshot<? extends IResource>> overwrittenResources = new ArrayList<>();
    for (IResource source : resources) {
      IPath destinationPath = pathIncludesName ? destination : destination.append(source.getName());
      IWorkspaceRoot workspaceRoot = getWorkspaceRoot();
      IResource existing = workspaceRoot.findMember(destinationPath);
      if (source.getType() == IResource.FOLDER && existing != null) {
        if (existing.getType() == IResource.FOLDER) {
          // a folder is merged into the existing one: its members are copied over it
          IResource[] children = ((IContainer) source).members();
          overwrittenResources.addAll(Arrays.asList(copy(children, destinationPath, resourcesAtDestination, monitor,
              uiInfo, false)));
        } else {
          overwrittenResources.addAll(Arrays.asList(delete(new IResource[] { existing }, monitor, uiInfo, false)));
          source.copy(destinationPath, IResource.SHALLOW, monitor);
          resourcesAtDestination.add(getWorkspaceRoot().findMember(destinationPath));
        }
      } else if (existing != null) {
        if (source.getType() == IResource.FILE && existing.getType() == IResource.FILE) {
          overwrittenResources.add(copyOverExistingResource(source, existing, monitor, uiInfo, false));
          resourcesAtDestination.add(existing);
        } else {
          overwrittenResources.addAll(Arrays.asList(delete(new IResource[] { existing }, monitor, uiInfo, false)));
          source.copy(destinationPath, IResource.SHALLOW, monitor);
          resourcesAtDestination.add(getWorkspaceRoot().findMember(destinationPath));
        }
      } else {
        IPath parentPath = pathIncludesName ? destination.removeLastSegments(1) : destination;
        IContainer generatedParent = generateContainers(parentPath);
        source.copy(destinationPath, IResource.SHALLOW, monitor);
        resourcesAtDestination.add(generatedParent == null ? getWorkspaceRoot().findMember(destinationPath)
            : generatedParent);
      }
    }
    return overwrittenResources.toArray(new IResourceSnapshot<?>[overwrittenResources.size()]);
  }

  /** Moves the resources to the destination, like {@link #copy} (the sources are gone afterwards). */
  static IResourceSnapshot<? extends IResource>[] move(IResource[] resources, IPath destination,
      List<IResource> resourcesAtDestination, List<IPath> reverseDestinations, IProgressMonitor mon,
      IAdaptable uiInfo, boolean pathIncludesName) throws CoreException {
    List<IResourceSnapshot<? extends IResource>> overwrittenResources = new ArrayList<>();
    for (IResource resource : resources) {
      IPath destinationPath = pathIncludesName ? destination : destination.append(resource.getName());
      IResource existing = getWorkspaceRoot().findMember(destinationPath);
      if (resource.getType() == IResource.FOLDER && existing != null && existing.getType() == IResource.FOLDER) {
        // a folder is merged into the existing one: its members are moved, then the emptied folder is deleted
        IResource[] children = ((IContainer) resource).members();
        overwrittenResources.addAll(Arrays.asList(move(children, destinationPath, resourcesAtDestination,
            reverseDestinations, mon, uiInfo, false)));
        delete(resource, mon, uiInfo, false);
      } else if (existing != null) {
        if (resource.getType() == IResource.FILE && existing.getType() == IResource.FILE) {
          reverseDestinations.add(resource.getFullPath());
          overwrittenResources.add(copyOverExistingResource(resource, existing, mon, uiInfo, true));
          resourcesAtDestination.add(existing);
        } else {
          overwrittenResources.addAll(Arrays.asList(delete(new IResource[] { existing }, mon, uiInfo, false)));
          reverseDestinations.add(resource.getFullPath());
          resource.move(destinationPath, IResource.SHALLOW | IResource.KEEP_HISTORY, mon);
          resourcesAtDestination.add(getWorkspaceRoot().findMember(destinationPath));
        }
      } else {
        reverseDestinations.add(resource.getFullPath());
        IPath parentPath = pathIncludesName ? destination.removeLastSegments(1) : destination;
        IContainer generatedParent = generateContainers(parentPath);
        resource.move(destinationPath, IResource.SHALLOW | IResource.KEEP_HISTORY, mon);
        resourcesAtDestination.add(generatedParent == null ? getWorkspaceRoot().findMember(destinationPath)
            : generatedParent);
      }
    }
    return overwrittenResources.toArray(new IResourceSnapshot<?>[overwrittenResources.size()]);
  }

  /** Recreates the resources of the snapshots (the undo of a delete, or of an overwrite). */
  static IResource[] recreate(IResourceSnapshot<? extends IResource>[] resourcesToRecreate, IProgressMonitor monitor,
      IAdaptable uiInfo) throws CoreException {
    List<CoreException> exceptions = new ArrayList<>();
    IResource[] resourcesToReturn = new IResource[resourcesToRecreate.length];
    for (int i = 0; i < resourcesToRecreate.length; ++i) {
      try {
        resourcesToReturn[i] = resourcesToRecreate[i].createResource(monitor);
      } catch (CoreException e) {
        exceptions.add(e);
      }
    }
    IStatus result = createResult(exceptions);
    if (!result.isOK()) {
      throw new CoreException(result);
    }
    return resourcesToReturn;
  }

  /** Overwrites an existing file with the contents of the source (kept in the local history), a snapshot of it. */
  private static IResourceSnapshot<IResource> copyOverExistingResource(IResource source, IResource existing,
      IProgressMonitor monitor, IAdaptable uiInfo, boolean deleteSourceFile) throws CoreException {
    if (!(source instanceof IFile file && existing instanceof IFile existingFile)) {
      return null;
    }
    if (!validateEdit(file, existingFile, getShell(uiInfo))) {
      return null;
    }
    IResourceSnapshot<IResource> fileDescription = ResourceSnapshotFactory.fromResource(existingFile);
    try (InputStream contents = file.getContents()) {
      existingFile.setContents(contents, IResource.KEEP_HISTORY, monitor);
    } catch (IOException closeException) {
      // the source is closed
    }
    fileDescription.recordStateFromHistory(monitor);
    if (deleteSourceFile) {
      file.delete(IResource.KEEP_HISTORY, monitor);
    }
    return fileDescription;
  }

  /**
   * Validates the destination file if it is read-only and additionally the source file if both are read-only. Returns
   * true if both files could be made writeable.
   */
  private static boolean validateEdit(IFile source, IFile destination, Shell shell) {
    if (destination.isReadOnly()) {
      IWorkspace workspace = WorkspaceUndoUtil.getWorkspace();
      IStatus status;
      if (source.isReadOnly()) {
        status = workspace.validateEdit(new IFile[] { source, destination }, shell);
      } else {
        status = workspace.validateEdit(new IFile[] { destination }, shell);
      }
      return status.isOK();
    }
    return true;
  }

  /** The innermost first folder of a snapshot (where a new member is added). */
  public static IContainerSnapshot<? extends IContainer> getFirstLeafFolder(
      IContainerSnapshot<? extends IContainer> parent) {
    IResourceSnapshot<? extends IResource>[] members = parent.getMembers();
    if (members == null || members.length == 0) {
      return parent;
    }
    for (IResourceSnapshot<? extends IResource> member : members) {
      if (member instanceof IContainerSnapshot) {
        return getFirstLeafFolder((IContainerSnapshot<? extends IResource>) member);
      }
    }
    return parent;
  }

  /** Creates the missing folders of a path; the folder created first is returned (null when all exist). */
  private static IContainer generateContainers(IPath path) throws CoreException {
    if (path.segmentCount() == 0) {
      return null;
    }
    if (getWorkspaceRoot().findMember(path) != null) {
      return null;
    }
    IResource container = path.segmentCount() == 1 ? getWorkspaceRoot().getProject(path.segment(0))
        : getWorkspaceRoot().getFolder(path);
    IResourceSnapshot<? extends IContainer> containerDescription = ResourceSnapshotFactory
        .fromContainer((IContainer) container);
    container = containerDescription.createResourceHandle();
    containerDescription.createExistentResourceFromHandle(new NullProgressMonitor());
    return (IContainer) container;
  }

  /** One failure is returned as it is; several are a multi status ("Problems deleting"). */
  static IStatus createResult(List<CoreException> exceptions) {
    if (exceptions.isEmpty()) {
      return Status.OK_STATUS;
    }
    if (exceptions.size() == 1) {
      return exceptions.get(0).getStatus();
    }
    MultiStatus multi = new MultiStatus(ResourcesPlugin.PI_RESOURCES, 0,
        UndoMessages.AbstractResourcesOperation_deletionExceptionMessage, null);
    for (CoreException exception : exceptions) {
      IStatus status = exception.getStatus();
      multi.add(new Status(status.getSeverity(), status.getPlugin(), status.getCode(), status.getMessage(), exception));
    }
    return multi;
  }
}
