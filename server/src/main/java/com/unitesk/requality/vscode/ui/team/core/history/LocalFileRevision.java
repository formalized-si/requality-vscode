package com.unitesk.requality.vscode.ui.team.core.history;

import java.io.InputStream;
import java.net.URI;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFileState;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.team.core.history.provider.FileRevision;

/**
 * {@code org.eclipse.team.internal.core.history.LocalFileRevision}: a local file revision for the History view. It wraps
 * either a local history state ({@link IFileState}) of a file or the current {@link IFile} (see
 * {@link #setBaseRevision(IFileRevision)}).
 */
public class LocalFileRevision extends FileRevision {
  /** Used for wrapping local file history items. */
  private final IFileState state;
  /** Used for displaying the "real" current file. */
  private final IFile file;
  /** Used for displaying which base revision. */
  private IFileRevision baseRevision;

  /** Wraps a local history state. */
  public LocalFileRevision(IFileState state) {
    this.state = state;
    this.file = null;
    this.baseRevision = null;
  }

  /** Wraps the current state of a file. */
  public LocalFileRevision(IFile file) {
    this.file = file;
    this.baseRevision = null;
    this.state = null;
  }

  @Override
  public String getContentIdentifier() {
    if (file != null) {
      return "Workspace File" + (baseRevision == null ? "" : " (" + baseRevision.getContentIdentifier() + ")");
    }
    return "";
  }

  @Override
  public String getAuthor() {
    return "";
  }

  @Override
  public String getComment() {
    if (file != null) {
      return "The current workspace file";
    }
    return null;
  }

  @Override
  public ITag[] getTags() {
    return new ITag[0];
  }

  @Override
  public IStorage getStorage(IProgressMonitor monitor) throws CoreException {
    if (file != null) {
      return file;
    }
    return state;
  }

  @Override
  public String getName() {
    if (file != null) {
      return file.getName();
    }
    return state.getName();
  }

  @Override
  public long getTimestamp() {
    if (file != null) {
      return file.getLocalTimeStamp();
    }
    return state.getModificationTime();
  }

  /** A local revision generally exists; when it does not, this tells the truth. */
  @Override
  public boolean exists() {
    if (file != null) {
      return file.exists();
    }
    return state.exists();
  }

  /** Associates a base revision with the current file (the revision the workspace file is based on). */
  public void setBaseRevision(IFileRevision baseRevision) {
    this.baseRevision = baseRevision;
  }

  @Override
  public boolean isPropertyMissing() {
    return true;
  }

  @Override
  public IFileRevision withAllProperties(IProgressMonitor monitor) {
    return this;
  }

  public boolean isPredecessorOf(IFileRevision revision) {
    return getTimestamp() < revision.getTimestamp();
  }

  public boolean isDescendentOf(IFileRevision revision) {
    return getTimestamp() > revision.getTimestamp();
  }

  @Override
  public URI getURI() {
    if (file != null) {
      return file.getLocationURI();
    }
    return null;
  }

  public IFile getFile() {
    return file;
  }

  public IFileState getState() {
    return state;
  }

  /** Whether this revision is the current state of the workspace file. */
  public boolean isCurrentState() {
    return file != null;
  }

  /** The contents of the revision (the state's or the file's). */
  InputStream contents() throws CoreException {
    return file != null ? file.getContents() : state.getContents();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof LocalFileRevision other) {
      if (file != null && other.file != null) {
        return file.equals(other.file);
      }
      if (state != null && other.state != null) {
        return statesEqual(state, other.state);
      }
    }
    return false;
  }

  private static boolean statesEqual(IFileState s1, IFileState s2) {
    return s1.getFullPath().equals(s2.getFullPath()) && s1.getModificationTime() == s2.getModificationTime();
  }

  @Override
  public int hashCode() {
    if (file != null) {
      return file.hashCode();
    }
    if (state != null) {
      return (int) state.getModificationTime();
    }
    return super.hashCode();
  }
}
