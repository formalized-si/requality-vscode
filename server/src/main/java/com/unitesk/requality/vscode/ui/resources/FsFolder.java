package com.unitesk.requality.vscode.ui.resources;

import java.io.File;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@link IFolder} over a directory. */
final class FsFolder extends FsContainer implements IFolder {
  private final ProjectRef project;

  FsFolder(ProjectRef project, IPath rel) {
    super(rel);
    this.project = project;
  }

  @Override
  public File toFile() {
    File base = project.toFile();
    return base == null ? null : new File(base, rel.toOSString());
  }

  @Override
  public int getType() {
    return FOLDER;
  }

  @Override
  public IProject getProject() {
    return project;
  }

  @Override
  public void create(boolean force, boolean local, IProgressMonitor monitor) throws CoreException {
    create(force ? FORCE : NONE, local, monitor);
  }

  @Override
  public void create(int updateFlags, boolean local, IProgressMonitor monitor) throws CoreException {
    File f = toFile();
    if (f.isDirectory() && (updateFlags & FORCE) == 0)
      throw error("Resource already exists: " + getFullPath(), null);
    if (!f.isDirectory() && !f.mkdirs())
      throw error("Could not create folder " + getFullPath(), null);
  }
}
