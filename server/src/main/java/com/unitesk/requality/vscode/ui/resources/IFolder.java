package com.unitesk.requality.vscode.ui.resources;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.core.resources.IFolder}. */
public interface IFolder extends IContainer {
  void create(boolean force, boolean local, IProgressMonitor monitor) throws CoreException;

  void create(int updateFlags, boolean local, IProgressMonitor monitor) throws CoreException;
}
