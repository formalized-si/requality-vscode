package com.unitesk.requality.vscode.ui.resources;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.core.resources.IWorkspaceRunnable}. */
@FunctionalInterface
public interface IWorkspaceRunnable {
  void run(IProgressMonitor monitor) throws CoreException;
}
