package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.FileSystemExportOperation}: copies the selected resources, and the
 * members of the selected folders, to a directory of the local file system. With the directory structure the resources
 * keep their workspace paths below the directory; without it the files are written directly into the directory.
 */
public class FileSystemExportOperation implements IRunnableWithProgress {
  private final List<IResource> resources;
  private final File destination;
  private final boolean createDirectoryStructure;
  private final IOverwriteQuery overwriteQuery;
  private boolean overwriteAll;
  private boolean noToAll;
  private final List<String> problems = new ArrayList<>();

  /**
   * @param resources the resources to export (folders export their members)
   * @param destination the directory the resources are exported to
   * @param createDirectoryStructure whether the workspace folders are created in the destination
   * @param overwriteQuery the query for existing files, or null to overwrite them
   */
  public FileSystemExportOperation(List<IResource> resources, File destination, boolean createDirectoryStructure,
      IOverwriteQuery overwriteQuery) {
    this.resources = resources;
    this.destination = destination;
    this.createDirectoryStructure = createDirectoryStructure;
    this.overwriteQuery = overwriteQuery;
  }

  /** Overwrites the existing files without asking. */
  public void setOverwriteResources(boolean value) {
    overwriteAll = value;
  }

  /** The problems of the export (one message per resource that was not exported). */
  public List<String> getProblems() {
    return problems;
  }

  @Override
  public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
    monitor.beginTask("Exporting", 100);
    try {
      for (IResource r : resources) {
        if (monitor.isCanceled())
          throw new OperationCanceledException();
        export(r, monitor);
      }
    } catch (OperationCanceledException e) {
      throw new InterruptedException();
    } finally {
      monitor.done();
    }
  }

  private void export(IResource resource, IProgressMonitor monitor) {
    try {
      if (resource instanceof IProject p && !p.isOpen())
        return;
      if (resource instanceof IContainer c) {
        if (createDirectoryStructure)
          Files.createDirectories(targetFor(resource).toPath());
        for (IResource member : c.members())
          export(member, monitor);
      } else if (resource instanceof IFile f) {
        exportFile(f, monitor);
      }
    } catch (CoreException | IOException e) {
      problems.add(resource.getFullPath() + ": " + e.getMessage());
    }
  }

  private void exportFile(IFile file, IProgressMonitor monitor) throws CoreException, IOException {
    File target = targetFor(file);
    monitor.subTask(file.getName());
    if (target.exists()) {
      String answer = overwriteAll ? IOverwriteQuery.ALL : noToAll ? IOverwriteQuery.NO_ALL
          : overwriteQuery == null ? IOverwriteQuery.YES : overwriteQuery.queryOverwrite(target.getPath());
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
    if (target.getParentFile() != null)
      Files.createDirectories(target.getParentFile().toPath());
    try (InputStream in = file.getContents(); OutputStream out = Files.newOutputStream(target.toPath())) {
      in.transferTo(out);
    }
    monitor.worked(1);
  }

  /** The file of the resource in the destination: with the directory structure its workspace path, else its name. */
  File targetFor(IResource resource) {
    File f = destination;
    if (createDirectoryStructure) {
      for (int i = 0; i < resource.getFullPath().segmentCount(); i++)
        f = new File(f, resource.getFullPath().segment(i));
    } else {
      f = new File(f, resource.getName());
    }
    return f;
  }
}
