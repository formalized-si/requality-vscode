package com.unitesk.requality.vscode.ui.resources;

import java.io.InputStream;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;

/**
 * {@code org.eclipse.core.resources.IFileState}: a state of a file kept in the local history (its contents and
 * modification time when the state was saved).
 */
public interface IFileState extends IStorage {
  @Override
  InputStream getContents() throws CoreException;

  long getModificationTime();

  @Override
  IPath getFullPath();

  @Override
  String getName();

  boolean exists();

  /** {@code FileState#isReadOnly()}: a state of the local history is read-only. */
  @Override
  default boolean isReadOnly() {
    return true;
  }

  String getCharset();
}
