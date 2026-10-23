package com.unitesk.requality.vscode.ui.resources;

import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.core.resources.IProjectNature}: a project nature (plugin.xml org.eclipse.core.resources.natures). */
public interface IProjectNature {
  /** Called when the nature is added to a project (it typically adds its builders). */
  void configure() throws CoreException;

  /** Called when the nature is removed from a project. */
  void deconfigure() throws CoreException;

  IProject getProject();

  void setProject(IProject project);
}
