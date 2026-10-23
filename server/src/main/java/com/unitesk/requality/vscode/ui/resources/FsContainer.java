package com.unitesk.requality.vscode.ui.resources;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;

/** A directory of a workspace project. */
abstract class FsContainer extends FsResource implements IContainer {
  protected FsContainer(IPath rel) {
    super(rel);
  }

  private ProjectRef project() {
    return (ProjectRef) getProject();
  }

  @Override
  public IResource[] members() throws CoreException {
    return members(NONE);
  }

  @Override
  public IResource[] members(int memberFlags) throws CoreException {
    File dir = toFile();
    if (dir == null || !dir.isDirectory())
      throw error("Resource does not exist: " + getFullPath(), null);
    File[] files = dir.listFiles();
    List<IResource> r = new ArrayList<>();
    if (files != null) {
      Arrays.sort(files);
      for (File f : files) {
        IPath p = rel.append(f.getName());
        r.add(f.isDirectory() ? new FsFolder(project(), p) : new FsFile(project(), p));
      }
    }
    return r.toArray(new IResource[0]);
  }

  @Override
  public IResource findMember(String path) {
    // Container.findMember(String): the path appended as IPath.append does (on Windows a backslash separates)
    return findMember(new Path(path));
  }

  @Override
  public IResource findMember(String path, boolean includePhantoms) {
    return findMember(path);
  }

  @Override
  public IResource findMember(IPath path) {
    IPath p = rel.append(path.makeRelative());
    File f = new File(project().toFile(), p.toOSString());
    if (!f.exists())
      return null;
    if (p.segmentCount() == 0)
      return project();
    return f.isDirectory() ? new FsFolder(project(), p) : new FsFile(project(), p);
  }

  @Override
  public boolean exists(IPath path) {
    return findMember(path) != null;
  }

  @Override
  public IFile getFile(IPath path) {
    return new FsFile(project(), rel.append(path.makeRelative()));
  }

  @Override
  public IFolder getFolder(IPath path) {
    return new FsFolder(project(), rel.append(path.makeRelative()));
  }

  /** {@code Container.getFile(String)}: the name appended as a path (IPath.append: on Windows a backslash separates). */
  @Override
  public IFile getFile(String name) {
    return getFile(new Path(name));
  }

  /** {@code Container.getFolder(String)}: the name appended as a path (IPath.append: on Windows a backslash separates). */
  @Override
  public IFolder getFolder(String name) {
    return getFolder(new Path(name));
  }

  /** The charset set on the container (in the project's resource preferences), else the one of its parent. */
  @Override
  public String getDefaultCharset() {
    if (this instanceof IWorkspaceRoot)
      return ResourcesPlugin.getEncoding();
    String own = ProjectSettings.get(project(), ProjectSettings.RESOURCES_PREFS, ProjectSettings.encodingKey(this));
    if (own != null)
      return own;
    if (this instanceof IProject)
      return ResourcesPlugin.getEncoding();
    try {
      IContainer parent = getParent();
      return parent == null ? ResourcesPlugin.getEncoding() : parent.getDefaultCharset();
    } catch (CoreException e) {
      return ResourcesPlugin.getEncoding();
    }
  }

  /** Sets the charset of the container's files; null removes it (the inherited charset applies again). */
  @Override
  public void setDefaultCharset(String charset, IProgressMonitor monitor) throws CoreException {
    try {
      ProjectSettings.put(project(), ProjectSettings.RESOURCES_PREFS, ProjectSettings.encodingKey(this), charset);
    } catch (java.io.IOException e) {
      throw FsResource.error("Could not write the encoding of " + getFullPath(), e);
    }
  }
}
