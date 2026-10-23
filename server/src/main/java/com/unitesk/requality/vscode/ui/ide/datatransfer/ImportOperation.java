package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.ide.wizards.IImportStructureProvider;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.Status;

/**
 * {@code org.eclipse.ui.wizards.datatransfer.ImportOperation}: imports the selected files and folders of an import
 * source (a file system folder, an archive) into a container of the workspace. The folder structure below the source
 * is kept under the destination; existing files are overwritten when the overwrite query says so.
 */
public class ImportOperation implements IRunnableWithProgress {
  private final IPath destinationPath;
  private final Object source;
  private final IImportStructureProvider provider;
  private final IOverwriteQuery overwriteQuery;
  private List<?> selectedFiles;
  private boolean createContainerStructure = true;
  private boolean overwriteAll;
  private boolean noToAll;
  private final List<IStatus> errors = new ArrayList<>();

  /**
   * Imports the children of the source.
   *
   * @param containerPath the workspace path of the destination container
   * @param source the source element (a folder of the provider)
   * @param provider the structure of the source
   * @param overwriteImplementor the query for existing resources
   */
  public ImportOperation(IPath containerPath, Object source, IImportStructureProvider provider,
      IOverwriteQuery overwriteImplementor) {
    this(containerPath, source, provider, overwriteImplementor, null);
  }

  /**
   * Imports the selected files and folders below the source.
   *
   * @param containerPath the workspace path of the destination container
   * @param source the source element
   * @param provider the structure of the source
   * @param overwriteImplementor the query for existing resources
   * @param filesToImport the selected elements of the provider, or null for the whole source
   */
  public ImportOperation(IPath containerPath, Object source, IImportStructureProvider provider,
      IOverwriteQuery overwriteImplementor, List<?> filesToImport) {
    this.destinationPath = containerPath;
    this.source = source;
    this.provider = provider;
    this.overwriteQuery = overwriteImplementor;
    this.selectedFiles = filesToImport;
  }

  /**
   * Whether the folders between the source and the selected elements are created in the destination. (When false the
   * elements are created directly in the destination.)
   */
  public void setCreateContainerStructure(boolean value) {
    createContainerStructure = value;
  }

  /** Overwrites the existing resources without asking (the overwrite option of the page). */
  public void setOverwriteResources(boolean value) {
    overwriteAll = value;
  }

  /** The status of the operation: OK, or an error that lists the resources that were not imported. */
  public IStatus getStatus() {
    if (errors.isEmpty())
      return new Status(IStatus.OK, "com.unitesk.requality", "OK");
    return new Status(IStatus.ERROR, "com.unitesk.requality", errors.size() + " resources could not be imported: "
        + errors.get(0).getMessage());
  }

  @Override
  public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
    monitor.beginTask("Importing resources", 100);
    try {
      if (selectedFiles == null)
        importObject(source, monitor, true);
      else
        for (Object o : selectedFiles) {
          if (monitor.isCanceled())
            throw new OperationCanceledException();
          importObject(o, monitor, true);
        }
    } catch (OperationCanceledException e) {
      throw new InterruptedException();
    } catch (CoreException e) {
      errors.add(e.getStatus());
    } finally {
      monitor.done();
    }
  }

  /** Imports one element: a folder recursively, a file as it is. */
  private void importObject(Object o, IProgressMonitor monitor, boolean top) throws CoreException {
    if (monitor.isCanceled())
      throw new OperationCanceledException();
    if (provider.isFolder(o)) {
      if (createContainerStructure || !top)
        containerFor(o, true);
      List<?> children = provider.getChildren(o);
      if (children != null)
        for (Object child : children)
          importObject(child, monitor, false);
      return;
    }
    importFile(o, monitor, top);
  }

  private void importFile(Object file, IProgressMonitor monitor, boolean top) throws CoreException {
    IContainer container = containerFor(file, false);
    IFile target = container.getFile(new Path(lastSegment(file)));
    monitor.subTask(provider.getLabel(file));
    if (target.exists()) {
      String answer = overwriteAll ? IOverwriteQuery.ALL : noToAll ? IOverwriteQuery.NO_ALL
          : overwriteQuery == null ? IOverwriteQuery.YES : overwriteQuery.queryOverwrite(target.getFullPath().toString());
      if (IOverwriteQuery.CANCEL.equals(answer))
        throw new OperationCanceledException();
      if (IOverwriteQuery.NO.equals(answer))
        return;
      if (IOverwriteQuery.NO_ALL.equals(answer)) {
        noToAll = true;
        return;
      }
      if (IOverwriteQuery.ALL.equals(answer))
        overwriteAll = true;
    }
    try (InputStream in = provider.getContents(file)) {
      if (in == null)
        throw new CoreException(new Status(IStatus.ERROR, "com.unitesk.requality", "Cannot read " + provider.getFullPath(file)));
      target.create(in, true, null);
    } catch (IOException e) {
      throw new CoreException(new Status(IStatus.ERROR, "com.unitesk.requality", "Cannot import " + provider.getFullPath(file), e));
    }
    monitor.worked(1);
  }

  /** The container the element is imported into: the destination plus the folders of the element below the source. */
  private IContainer containerFor(Object element, boolean isFolder) throws CoreException {
    IPath relative = relativePath(element);
    IPath folders = isFolder ? relative : relative.removeLastSegments(1);
    if (!createContainerStructure)
      folders = new Path("");
    IContainer current = destinationContainer();
    for (int i = 0; i < folders.segmentCount(); i++) {
      IFolder f = current.getFolder(new Path(folders.segment(i)));
      if (!f.exists())
        f.create(true, true, null);
      current = f;
    }
    return current;
  }

  /** The destination container, the project or folder at the destination path (created when missing). */
  private IContainer destinationContainer() throws CoreException {
    if (destinationPath == null || destinationPath.segmentCount() == 0)
      throw new CoreException(new Status(IStatus.ERROR, "com.unitesk.requality", "No destination container"));
    IContainer current = ResourcesPlugin.getWorkspace().getRoot().getProject(destinationPath.segment(0));
    for (int i = 1; i < destinationPath.segmentCount(); i++) {
      IFolder f = current.getFolder(new Path(destinationPath.segment(i)));
      if (!f.exists())
        f.create(true, true, null);
      current = f;
    }
    return current;
  }

  /** The path of the element below the source. */
  private IPath relativePath(Object element) {
    IPath full = new Path(provider.getFullPath(element));
    IPath base = new Path(provider.getFullPath(source));
    if (base.segmentCount() >= full.segmentCount())
      return new Path("");
    return full.removeFirstSegments(base.segmentCount());
  }

  private String lastSegment(Object element) {
    IPath rel = relativePath(element);
    return rel.isEmpty() ? provider.getLabel(element) : rel.lastSegment();
  }
}
