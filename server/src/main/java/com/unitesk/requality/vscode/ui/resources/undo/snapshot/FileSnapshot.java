package com.unitesk.requality.vscode.ui.resources.undo.snapshot;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFileState;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.core.internal.resources.undo.snapshot.FileSnapshot}: a file, its charset and the contents of the
 * state of its local history with the file's time stamp, else its latest state ({@link #recordStateFromHistory}).
 */
class FileSnapshot extends AbstractResourceSnapshot<IFile> {
  String name;
  String charset;
  private IFileContentSnapshot fileContentDescription;

  FileSnapshot(IFile file) {
    super(file);
    this.name = file.getName();
    try {
      this.charset = file.getCharset();
    } catch (CoreException e) {
      // no charset is restored
    }
  }

  FileSnapshot(IFile file, IFileContentSnapshot fileContentDescription) {
    super(file);
    this.name = file.getName();
    this.charset = null;
    this.fileContentDescription = fileContentDescription;
  }

  @Override
  public void recordStateFromHistory(IProgressMonitor monitor) throws CoreException {
    IFile resource = createResourceHandle();
    IFileState[] states = resource.getHistory(monitor);
    if (states.length > 0) {
      final IFileState state = getMatchingFileState(states);
      this.fileContentDescription = new IFileContentSnapshot() {
        @Override
        public boolean exists() {
          return state.exists();
        }

        @Override
        public InputStream getContents() throws CoreException {
          return state.getContents();
        }

        @Override
        public String getCharset() {
          return state.getCharset();
        }
      };
    }
  }

  @Override
  public IFile createResourceHandle() {
    IPath fullPath = parent.getFullPath().append(name);
    return getWorkspace().getRoot().getFile(fullPath);
  }

  @Override
  public void createExistentResourceFromHandle(IProgressMonitor mon) throws CoreException {
    IFile fileHandle = createResourceHandle();
    if (fileHandle.exists()) {
      return;
    }
    InputStream contents = new ByteArrayInputStream(new byte[0]);
    if (fileContentDescription != null && fileContentDescription.exists()) {
      contents = fileContentDescription.getContents();
    }
    fileHandle.create(contents, false, mon);
    if (charset != null) {
      fileHandle.setCharset(charset, mon);
    }
  }

  @Override
  public boolean isValid() {
    return super.isValid() && fileContentDescription != null && fileContentDescription.exists();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean verifyExistence(boolean checkMembers) {
    return parent.findMember(name) != null;
  }

  /** The state of the history with the local time stamp of the file, else the latest state. */
  private IFileState getMatchingFileState(IFileState[] states) {
    for (IFileState state : states) {
      if (localTimeStamp == state.getModificationTime()) {
        return state;
      }
    }
    return states[0];
  }
}
