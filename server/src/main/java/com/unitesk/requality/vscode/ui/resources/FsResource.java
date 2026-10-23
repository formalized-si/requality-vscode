package com.unitesk.requality.vscode.ui.resources;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.QualifiedName;
import com.unitesk.requality.vscode.ui.runtime.Status;

/** A resource backed by a file or directory of a workspace project. */
abstract class FsResource implements IResource {
  /** Project-relative path; empty for the project itself. */
  protected final IPath rel;

  protected FsResource(IPath rel) {
    this.rel = rel;
  }

  /** The file or directory. */
  public abstract File toFile();

  static CoreException error(String message, Throwable t) {
    return new CoreException(new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES, message, t));
  }

  @Override
  public String getName() {
    String s = rel.lastSegment();
    return s == null ? "" : s;
  }

  @Override
  public IPath getFullPath() {
    return getProject().getFullPath().append(rel);
  }

  @Override
  public String getPersistentProperty(QualifiedName key) throws CoreException {
    IPath location = getLocation();
    return location == null ? null : ResourceProperties.get(location.toOSString(), key);
  }

  @Override
  public void setPersistentProperty(QualifiedName key, String value) throws CoreException {
    IPath location = getLocation();
    if (location != null)
      ResourceProperties.set(location.toOSString(), key, value);
  }

  @Override
  public IPath getLocation() {
    File f = toFile();
    return f == null ? null : Path.fromOSString(f.getAbsolutePath());
  }

  @Override
  public URI getLocationURI() {
    File f = toFile();
    return f == null ? null : f.getAbsoluteFile().toURI();
  }

  @Override
  public IPath getProjectRelativePath() {
    return rel;
  }

  @Override
  public IPath getRawLocation() {
    return getLocation();
  }

  @Override
  public IContainer getParent() {
    if (rel.segmentCount() <= 1)
      return getProject();
    return new FsFolder((ProjectRef) getProject(), rel.removeLastSegments(1));
  }

  @Override
  public IWorkspace getWorkspace() {
    return ResourcesPlugin.getWorkspace();
  }

  @Override
  public boolean exists() {
    File f = toFile();
    if (f == null || !f.exists())
      return false;
    return getType() == FILE ? f.isFile() : f.isDirectory();
  }

  @Override
  public boolean isAccessible() {
    return exists();
  }

  @Override
  public String getFileExtension() {
    return rel.getFileExtension();
  }

  @Override
  public long getLocalTimeStamp() {
    File f = toFile();
    return f != null && f.exists() ? f.lastModified() : -1;
  }

  @Override
  public long setLocalTimeStamp(long value) throws CoreException {
    File f = toFile();
    if (f != null && f.exists())
      f.setLastModified(value);
    return getLocalTimeStamp();
  }

  @Override
  public boolean isSynchronized(int depth) {
    return true;
  }

  @Override
  public boolean isReadOnly() {
    File f = toFile();
    return f != null && f.exists() && readOnly(f);
  }

  /**
   * The read-only attribute of a file as Eclipse's file system reads it: the owner write permission (POSIX) or the
   * read-only attribute (DOS); not an access check, which the super-user always passes.
   */
  static boolean readOnly(File f) {
    java.nio.file.Path p = f.toPath();
    try {
      if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        return !Files.getPosixFilePermissions(p).contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
      return Files.readAttributes(p, java.nio.file.attribute.DosFileAttributes.class).isReadOnly();
    } catch (java.io.IOException | UnsupportedOperationException e) {
      return !f.canWrite();
    }
  }

  @Override
  public boolean isHidden() {
    return getName().startsWith(".");
  }

  @Override
  public boolean isDerived() {
    return false;
  }

  @Override
  public boolean isLinked() {
    return false;
  }

  @Override
  public ResourceAttributes getResourceAttributes() {
    File f = toFile();
    if (f == null || !f.exists())
      return null;
    ResourceAttributes a = new ResourceAttributes();
    a.setReadOnly(readOnly(f));
    a.setExecutable(f.canExecute());
    a.setHidden(isHidden());
    a.setSymbolicLink(Files.isSymbolicLink(f.toPath()));
    return a;
  }

  @Override
  public void setResourceAttributes(ResourceAttributes attributes) throws CoreException {
    File f = toFile();
    if (f == null || !f.exists())
      throw error("Resource does not exist: " + getFullPath(), null);
    f.setWritable(!attributes.isReadOnly());
    f.setExecutable(attributes.isExecutable());
  }

  @Override
  public void refreshLocal(int depth, IProgressMonitor monitor) throws CoreException {
    // The file system is the model: nothing is cached. Changes made outside reach the server from the
    // extension's file watcher.
  }

  @Override
  public void delete(boolean force, IProgressMonitor monitor) throws CoreException {
    delete(force ? FORCE : NONE, monitor);
  }

  @Override
  public void delete(int updateFlags, IProgressMonitor monitor) throws CoreException {
    File f = toFile();
    if (f == null || !f.exists())
      return;
    // KEEP_HISTORY: the states of the files are saved before they are lost (IResource.delete as in Eclipse)
    if ((updateFlags & KEEP_HISTORY) != 0)
      FileHistory.keepTree(f, getFullPath());
    deleteTree(f);
    // as Eclipse, the markers of a deleted resource (and of its members) are removed, with its properties
    MarkerStore.delete(this, null, false, DEPTH_INFINITE);
    ResourceProperties.deleted(f);
    ResourceChanges.post(this, IResourceDelta.REMOVED, 0, null);
  }

  /** Deletes a file or a directory with all its content. */
  static void deleteTree(File f) throws CoreException {
    try (Stream<java.nio.file.Path> s = Files.walk(f.toPath())) {
      for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) s.sorted(Comparator.reverseOrder())::iterator)
        Files.deleteIfExists(p);
    } catch (IOException e) {
      throw error("Could not delete " + f.getPath(), e);
    }
  }

  /** Destination: absolute = workspace full path, relative = sibling of this resource. */
  protected File destination(IPath destination) throws CoreException {
    IPath full = destination.isAbsolute() ? destination : getParent().getFullPath().append(destination);
    File f = WorkspaceRoot.fileOf(full);
    if (f == null)
      throw error("Destination is not in the workspace: " + full, null);
    return f;
  }

  @Override
  public void copy(IPath destination, boolean force, IProgressMonitor monitor) throws CoreException {
    copy(destination, force ? FORCE : NONE, monitor);
  }

  @Override
  public void copy(IPath destination, int updateFlags, IProgressMonitor monitor) throws CoreException {
    File from = toFile();
    File to = destination(destination);
    try (Stream<java.nio.file.Path> s = Files.walk(from.toPath())) {
      for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) s::iterator) {
        java.nio.file.Path t = to.toPath().resolve(from.toPath().relativize(p).toString());
        if (Files.isDirectory(p))
          Files.createDirectories(t);
        else {
          Files.createDirectories(t.getParent());
          Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    } catch (IOException e) {
      throw error("Could not copy " + getFullPath() + " to " + destination, e);
    }
    // the copies get the persistent properties of the originals
    ResourceProperties.moved(from, to, true);
  }

  @Override
  public void move(IPath destination, boolean force, IProgressMonitor monitor) throws CoreException {
    move(destination, force ? FORCE : NONE, monitor);
  }

  @Override
  public void move(IPath destination, int updateFlags, IProgressMonitor monitor) throws CoreException {
    File to = destination(destination);
    IPath target = destination.isAbsolute() ? destination : getParent().getFullPath().append(destination);
    // Resource.move: an existing destination is an error (FORCE only concerns resources out of sync).
    if (to.exists())
      throw error("A resource already exists on disk '" + to.getPath() + "'.", null);
    try {
      if (to.getParentFile() != null)
        Files.createDirectories(to.getParentFile().toPath());
      FileMoves.move(toFile().toPath(), to.toPath());
    } catch (IOException e) {
      throw error("Could not move " + getFullPath() + " to " + destination, e);
    }
    // the markers and the persistent properties of the resource and its members move with it
    File from = toFile();
    MarkerStore.moved(getFullPath(), target);
    ResourceProperties.moved(from, to, false);
    ResourceChanges.post(this, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, target);
  }

  @Override
  public void touch(IProgressMonitor monitor) throws CoreException {
    File f = toFile();
    if (f != null && f.exists())
      f.setLastModified(System.currentTimeMillis());
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FsResource) || ((FsResource) o).getType() != getType())
      return false;
    File a = toFile();
    File b = ((FsResource) o).toFile();
    return a != null && b != null && a.getAbsoluteFile().equals(b.getAbsoluteFile());
  }

  @Override
  public int hashCode() {
    File f = toFile();
    return f == null ? 0 : f.getAbsoluteFile().hashCode();
  }

  @Override
  public String toString() {
    String prefix = getType() == FILE ? "L" : getType() == FOLDER ? "F" : getType() == PROJECT ? "P" : "R";
    return prefix + "/" + getFullPath().makeRelative();
  }
}
