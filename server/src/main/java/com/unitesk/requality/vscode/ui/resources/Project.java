package com.unitesk.requality.vscode.ui.resources;

import com.unitesk.requality.vscode.ui.runtime.IPath;

/**
 * {@code org.eclipse.core.internal.resources.Project}: the implementation class of the projects of the workspace
 * ({@link ProjectRef} of the workspace emulation); code that tests for it (instanceof Project) sees the projects.
 */
public abstract class Project extends FsContainer implements IProject {
  protected Project(IPath rel) {
    super(rel);
  }
}
