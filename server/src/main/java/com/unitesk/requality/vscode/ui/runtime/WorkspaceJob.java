package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.resources.WorkspaceJob}. */
public abstract class WorkspaceJob extends Job {
  public WorkspaceJob(String name) {
    super(name);
  }

  public abstract IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException;

  @Override
  protected final IStatus run(IProgressMonitor monitor) {
    try {
      return runInWorkspace(monitor);
    } catch (CoreException e) {
      return e.getStatus();
    }
  }
}
