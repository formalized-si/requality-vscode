package com.unitesk.requality.vscode.ui.team.core.history;

import java.util.ArrayList;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFileState;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.team.internal.core.history.LocalFileHistory}: the local history of a workspace file: its states,
 * most recent first, and the current file when it exists.
 */
public class LocalFileHistory extends FileHistory {
  protected IFile file;
  /** The revisions (the states and the current file), or null until refreshed. */
  protected IFileRevision[] revisions;
  private final boolean includeCurrent;

  public LocalFileHistory(IFile file, boolean includeCurrent) {
    this.file = file;
    this.includeCurrent = includeCurrent;
  }

  @Override
  public IFileRevision[] getContributors(IFileRevision revision) {
    IFileRevision fileRevision = null;
    for (IFileRevision r : getFileRevisions()) {
      if (((LocalFileRevision) r).isPredecessorOf(revision)
          && (fileRevision == null || r.getTimestamp() > fileRevision.getTimestamp())) {
        fileRevision = r;
      }
    }
    return fileRevision == null ? new IFileRevision[0] : new IFileRevision[] { fileRevision };
  }

  @Override
  public IFileRevision getFileRevision(String id) {
    if (revisions != null) {
      for (IFileRevision revision : revisions) {
        if (revision.getContentIdentifier().equals(id)) {
          return revision;
        }
      }
    }
    return null;
  }

  @Override
  public IFileRevision[] getFileRevisions() {
    return revisions == null ? new IFileRevision[0] : revisions;
  }

  @Override
  public IFileRevision[] getTargets(IFileRevision revision) {
    ArrayList<IFileRevision> descendants = new ArrayList<>();
    for (IFileRevision r : getFileRevisions())
      if (((LocalFileRevision) r).isDescendentOf(revision))
        descendants.add(r);
    return descendants.toArray(new IFileRevision[0]);
  }

  /** Refreshes the revisions of the local file from its local history. */
  public void refresh(IProgressMonitor monitor) throws CoreException {
    monitor.beginTask("Refresh Local History", 300);
    try {
      LocalFileRevision currentRevision = includeRevisionForFile() ? new LocalFileRevision(file) : null;
      IFileState[] fileStates = file.getHistory(monitor);
      IFileRevision[] result = new IFileRevision[fileStates.length + (currentRevision != null ? 1 : 0)];
      for (int i = 0; i < fileStates.length; i++) {
        result[i] = new LocalFileRevision(fileStates[i]);
      }
      if (currentRevision != null) {
        result[fileStates.length] = currentRevision;
      }
      revisions = result;
    } finally {
      monitor.done();
    }
  }

  private boolean includeRevisionForFile() {
    return file.exists() && includeCurrent;
  }
}
