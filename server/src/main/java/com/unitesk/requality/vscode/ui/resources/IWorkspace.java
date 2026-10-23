package com.unitesk.requality.vscode.ui.resources;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.ISchedulingRule;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/** {@code org.eclipse.core.resources.IWorkspace}. */
public interface IWorkspace {
  int AVOID_UPDATE = 1;

  IWorkspaceRoot getRoot();

  void run(IWorkspaceRunnable action, IProgressMonitor monitor) throws CoreException;

  void run(IWorkspaceRunnable action, ISchedulingRule rule, int flags, IProgressMonitor monitor) throws CoreException;

  void addResourceChangeListener(IResourceChangeListener listener);

  void addResourceChangeListener(IResourceChangeListener listener, int eventMask);

  void removeResourceChangeListener(IResourceChangeListener listener);

  IResourceRuleFactory getRuleFactory();

  IProjectDescription newProjectDescription(String projectName);

  /**
   * {@code IWorkspace.loadProjectDescription}: the description of the project in the .project file at the path. A
   * missing file is a {@link ResourceException} caused by a {@code FileNotFoundException}.
   */
  IProjectDescription loadProjectDescription(com.unitesk.requality.vscode.ui.runtime.IPath path) throws CoreException;

  IStatus validateName(String segment, int typeMask);

  IStatus validateProjectLocation(IProject project, com.unitesk.requality.vscode.ui.runtime.IPath location);

  /**
   * {@code IWorkspace.validateEdit}: whether the read-only files can be edited (a file modification validator of a team
   * provider decides; without one the read-only files are reported).
   *
   * @param files the files that are going to be edited
   * @param context the context of the validation (the shell of the dialogs of the validator), may be null
   */
  IStatus validateEdit(IFile[] files, Object context);

  boolean isAutoBuilding();

  /** {@code IWorkspace.getDescription}: the settings of the workspace (Window &gt; Preferences &gt; General &gt; Workspace). */
  IWorkspaceDescription getDescription();

  /** {@code IWorkspace.setDescription}: applies the settings of a description. */
  void setDescription(IWorkspaceDescription description) throws CoreException;

  default void checkpoint(boolean build) {
  }

  default boolean isTreeLocked() {
    return false;
  }
}
