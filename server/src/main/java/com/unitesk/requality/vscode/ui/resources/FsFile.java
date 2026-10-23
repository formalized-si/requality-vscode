package com.unitesk.requality.vscode.ui.resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@link IFile} over a file. */
public final class FsFile extends FsResource implements IFile {
  private final ProjectRef project;

  FsFile(ProjectRef project, IPath rel) {
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
    return FILE;
  }

  @Override
  public IProject getProject() {
    return project;
  }

  private void write(InputStream source, boolean append) throws CoreException {
    File f = toFile();
    try {
      if (f.getParentFile() != null)
        f.getParentFile().mkdirs();
      try (OutputStream out = new FileOutputStream(f, append)) {
        if (source != null) {
          byte[] buf = new byte[8192];
          int n;
          while ((n = source.read(buf)) > 0)
            out.write(buf, 0, n);
        }
      }
    } catch (IOException e) {
      throw error("Could not write " + getFullPath(), e);
    } finally {
      if (source != null)
        try {
          source.close();
        } catch (IOException e) {
          // ignored
        }
    }
  }

  @Override
  public void create(InputStream source, boolean force, IProgressMonitor monitor) throws CoreException {
    create(source, force ? FORCE : NONE, monitor);
  }

  @Override
  public void create(InputStream source, int updateFlags, IProgressMonitor monitor) throws CoreException {
    if (toFile().exists() && (updateFlags & FORCE) == 0)
      throw error("Resource already exists: " + getFullPath(), null);
    write(source, false);
  }

  @Override
  public void appendContents(InputStream source, boolean force, boolean keepHistory, IProgressMonitor monitor)
      throws CoreException {
    if (keepHistory)
      FileHistory.keep(toFile(), getFullPath());
    write(source, true);
  }

  @Override
  public IFileState[] getHistory(IProgressMonitor monitor) {
    return FileHistory.states(getFullPath());
  }

  @Override
  public InputStream getContents() throws CoreException {
    try {
      return new FileInputStream(toFile());
    } catch (FileNotFoundException e) {
      throw error("Resource does not exist: " + getFullPath(), e);
    }
  }

  @Override
  public InputStream getContents(boolean force) throws CoreException {
    return getContents();
  }

  @Override
  public void setContents(InputStream source, boolean force, boolean keepHistory, IProgressMonitor monitor)
      throws CoreException {
    if (keepHistory)
      FileHistory.keep(toFile(), getFullPath());
    write(source, false);
  }

  /** IFile.setContents: with {@code KEEP_HISTORY} the contents replaced are saved in the local history first. */
  @Override
  public void setContents(InputStream source, int updateFlags, IProgressMonitor monitor) throws CoreException {
    if ((updateFlags & KEEP_HISTORY) != 0)
      FileHistory.keep(toFile(), getFullPath());
    write(source, false);
  }

  @Override
  public String getCharset() throws CoreException {
    return getCharset(false);
  }

  /** The charset set on the file (in the project's resource preferences), else the default of its container. */
  @Override
  public String getCharset(boolean checkImplicit) throws CoreException {
    String own = ProjectSettings.get(project, ProjectSettings.RESOURCES_PREFS, ProjectSettings.encodingKey(this));
    if (own != null)
      return own;
    IContainer parent = getParent();
    return parent == null ? ResourcesPlugin.getEncoding() : parent.getDefaultCharset();
  }

  /** Sets the charset of the file; null removes it (the container's default applies again). */
  @Override
  public void setCharset(String newCharset, IProgressMonitor monitor) throws CoreException {
    try {
      ProjectSettings.put(project, ProjectSettings.RESOURCES_PREFS, ProjectSettings.encodingKey(this), newCharset);
    } catch (IOException e) {
      throw FsResource.error("Could not write the encoding of " + getFullPath(), e);
    }
  }
}
