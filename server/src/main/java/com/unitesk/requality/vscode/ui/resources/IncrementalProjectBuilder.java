package com.unitesk.requality.vscode.ui.resources;

import java.util.Map;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.core.resources.IncrementalProjectBuilder}: run by the {@link BuildManager} for the projects whose
 * build specification names it. No resource deltas are recorded between builds: {@link #getDelta(IProject)} answers
 * null, which tells a builder to treat the build as a full one.
 */
public abstract class IncrementalProjectBuilder {
  public static final int FULL_BUILD = 6;
  public static final int INCREMENTAL_BUILD = 10;
  public static final int AUTO_BUILD = 9;
  public static final int CLEAN_BUILD = 15;

  private IProject project;
  private ICommand command;

  protected abstract IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor)
      throws CoreException;

  /** Discards what the builder built (Project > Clean...). */
  protected void clean(IProgressMonitor monitor) throws CoreException {
  }

  /** The project being built. */
  public IProject getProject() {
    return project;
  }

  public ICommand getCommand() {
    return command;
  }

  /** The delta since the last build: not recorded, so null (a builder then rebuilds everything). */
  public IResourceDelta getDelta(IProject project) {
    return null;
  }

  /** The next build of the project is a full build. */
  public void forgetLastBuiltState() {
    BuildManager.forget(project);
  }

  /** Called before the first build of the builder instance. */
  protected void startupOnInitialize() {
  }

  final void bind(IProject project, ICommand command) {
    this.project = project;
    this.command = command;
  }

  final IProject[] runBuild(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
    return build(kind, args, monitor);
  }

  final void runClean(IProgressMonitor monitor) throws CoreException {
    clean(monitor);
  }
}
