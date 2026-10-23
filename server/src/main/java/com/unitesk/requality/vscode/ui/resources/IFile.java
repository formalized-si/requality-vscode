package com.unitesk.requality.vscode.ui.resources;

import java.io.InputStream;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.core.resources.IFile}. */
public interface IFile extends IResource, IStorage {
  void create(InputStream source, boolean force, IProgressMonitor monitor) throws CoreException;

  void create(InputStream source, int updateFlags, IProgressMonitor monitor) throws CoreException;

  void appendContents(InputStream source, boolean force, boolean keepHistory, IProgressMonitor monitor)
      throws CoreException;

  @Override
  InputStream getContents() throws CoreException;

  InputStream getContents(boolean force) throws CoreException;

  void setContents(InputStream source, boolean force, boolean keepHistory, IProgressMonitor monitor)
      throws CoreException;

  void setContents(InputStream source, int updateFlags, IProgressMonitor monitor) throws CoreException;

  String getCharset() throws CoreException;

  String getCharset(boolean checkImplicit) throws CoreException;

  void setCharset(String newCharset, IProgressMonitor monitor) throws CoreException;

  /** {@code IFile.getHistory}: the saved states of the file in the local history, the most recent first. */
  default IFileState[] getHistory(IProgressMonitor monitor) throws CoreException {
    return new IFileState[0];
  }
}
