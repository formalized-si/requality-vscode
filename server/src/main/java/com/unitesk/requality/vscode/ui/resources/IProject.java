package com.unitesk.requality.vscode.ui.resources;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.core.resources.IProject}: a folder of the VS Code workspace with an Eclipse .project. */
public interface IProject extends IContainer {
  void create(IProgressMonitor monitor) throws CoreException;

  void create(IProjectDescription description, IProgressMonitor monitor) throws CoreException;

  void open(IProgressMonitor monitor) throws CoreException;

  void open(int updateFlags, IProgressMonitor monitor) throws CoreException;

  void close(IProgressMonitor monitor) throws CoreException;

  boolean isOpen();

  boolean hasNature(String natureId) throws CoreException;

  boolean isNatureEnabled(String natureId) throws CoreException;

  IProjectDescription getDescription() throws CoreException;

  void setDescription(IProjectDescription description, IProgressMonitor monitor) throws CoreException;

  void setDescription(IProjectDescription description, int updateFlags, IProgressMonitor monitor)
      throws CoreException;

  /** Renames (and, with a new location, moves) the project as the description says. */
  void move(IProjectDescription description, boolean force, IProgressMonitor monitor) throws CoreException;

  void move(IProjectDescription description, int updateFlags, IProgressMonitor monitor) throws CoreException;

  IProject[] getReferencedProjects() throws CoreException;

  /** IResource.copy of a project: a copy with the name and location (null: the default one) of the description. */
  void copy(IProjectDescription description, int updateFlags, IProgressMonitor monitor) throws CoreException;

  /** Deletes the project (with or without its content on disk). */
  void delete(boolean deleteContent, boolean force, IProgressMonitor monitor) throws CoreException;

  /** Runs the builders of the project's build specification (IncrementalProjectBuilder kinds). */
  void build(int kind, IProgressMonitor monitor) throws CoreException;

  /** The nature object; only the presence of the nature is known here. */
  default Object getNature(String natureId) throws CoreException {
    return hasNature(natureId) ? natureId : null;
  }

  /** The Requality tree of the project, when loaded (not in Eclipse: replaces the EclipseTracker lookup). */
  TreeDB getTreeDB();
}
