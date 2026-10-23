package com.unitesk.requality.vscode.ui.resources;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;

/**
 * The workspace build ({@code org.eclipse.core.internal.events.BuildManager}): the builders of a project are the
 * build commands of its .project, created from the {@code org.eclipse.core.resources.builders} extensions of
 * plugin.xml (extension id = builder name) and kept per project. A builder without a build state (never built, or
 * cleaned) gets a full build; a clean build calls {@code clean} and forgets the build state. Whether a project was
 * built and whether the workspace builds automatically is kept by the {@link Workspace}.
 */
public final class BuildManager {
  private static final String BUILDERS = "org.eclipse.core.resources.builders";
  private static final Map<String, IncrementalProjectBuilder> INSTANCES = new HashMap<>();
  /** Builds run one at a time (the workspace build rule). */
  private static final java.util.concurrent.locks.ReentrantLock BUILD_LOCK = new java.util.concurrent.locks.ReentrantLock();
  private static volatile boolean stopped;

  private BuildManager() {
  }

  private static Workspace workspace() {
    return ServerContext.server() == null ? null : ServerContext.server().workspace();
  }

  private static File dir(IProject project) {
    return project.getLocation() == null ? null : project.getLocation().toFile();
  }

  /** The builder instance of a build command of a project, or null when no plug-in contributes the builder. */
  private static synchronized IncrementalProjectBuilder builder(IProject project, ICommand command) {
    String key = project.getName() + "\n" + command.getBuilderName();
    IncrementalProjectBuilder b = INSTANCES.get(key);
    if (b != null)
      return b;
    for (IConfigurationElement ext : ExtensionRegistry.get().getConfigurationElementsFor(BUILDERS)) {
      if (!"builder".equals(ext.getName()) || ext.getDeclaringExtension() == null
          || !command.getBuilderName().equals(ext.getDeclaringExtension().getUniqueIdentifier()))
        continue;
      for (IConfigurationElement run : ext.getChildren("run")) {
        try {
          b = (IncrementalProjectBuilder) run.createExecutableExtension("class");
          b.bind(project, command);
          b.startupOnInitialize();
          INSTANCES.put(key, b);
          return b;
        } catch (CoreException | ClassCastException e) {
          RLogger.logError(e);
        }
      }
    }
    return null;
  }

  /** The workspace opens: builds may run. */
  public static void start() {
    stopped = false;
  }

  /** The workspace shuts down: no build starts any more and a running one is waited for. */
  public static void shutdown() {
    stopped = true;
    try {
      if (BUILD_LOCK.tryLock(30, java.util.concurrent.TimeUnit.SECONDS))
        BUILD_LOCK.unlock();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** IProject.build: runs the builders of the project (an open project with a build specification). */
  public static void build(IProject project, int kind, IProgressMonitor monitor) throws CoreException {
    if (stopped || project == null || !project.isAccessible())
      return;
    BUILD_LOCK.lock();
    try {
      buildLocked(project, kind, monitor);
    } finally {
      BUILD_LOCK.unlock();
    }
  }

  private static void buildLocked(IProject project, int kind, IProgressMonitor monitor) throws CoreException {
    IProgressMonitor pm = monitor == null ? new NullProgressMonitor() : monitor;
    Workspace ws = workspace();
    File dir = dir(project);
    boolean built = ws != null && dir != null && ws.isBuilt(dir);
    ICommand[] spec = project.getDescription().getBuildSpec();
    pm.beginTask("Building '" + project.getName() + "'", Math.max(1, spec.length));
    try {
      for (ICommand c : spec) {
        if (pm.isCanceled() || stopped)
          return;
        IncrementalProjectBuilder b = builder(project, c);
        if (b == null) {
          // Eclipse reports a missing builder ("Builder ... not found") and skips it.
          RLogger.logInfo("Skipping builder '" + c.getBuilderName() + "' for project '" + project.getName()
              + "'. Either the builder is missing from the install, or it belongs to a project nature that is missing or disabled.");
          continue;
        }
        b.bind(project, c);
        pm.subTask("Invoking '" + c.getBuilderName() + "' on '" + project.getName() + "'.");
        try {
          if (kind == IncrementalProjectBuilder.CLEAN_BUILD)
            b.runClean(pm);
          else
            b.runBuild(built ? kind : IncrementalProjectBuilder.FULL_BUILD,
                c.getArguments() == null ? new HashMap<>() : c.getArguments(), pm);
        } catch (RuntimeException e) {
          RLogger.logError(e);
        }
        pm.worked(1);
      }
    } finally {
      pm.done();
    }
    if (ws != null && dir != null)
      ws.setBuilt(dir, kind != IncrementalProjectBuilder.CLEAN_BUILD);
  }

  static void forget(IProject project) {
    Workspace ws = workspace();
    File dir = project == null ? null : dir(project);
    if (ws != null && dir != null)
      ws.setBuilt(dir, false);
  }

  /** The open projects of the workspace (IWorkspace build order: as listed). */
  public static List<IProject> openProjects() {
    List<IProject> r = new ArrayList<>();
    for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects())
      if (p.isAccessible())
        r.add(p);
    return r;
  }

  /** IWorkspace.build of the given projects (all open projects when null). */
  public static void build(List<IProject> projects, int kind, IProgressMonitor monitor) throws CoreException {
    for (IProject p : projects == null ? openProjects() : projects) {
      if (monitor != null && monitor.isCanceled())
        return;
      build(p, kind, monitor);
    }
  }

  /** The auto-build of a project that enters the workspace (it has no build state yet). */
  public static void autoBuild(File dir) {
    IContainer c = ResourcesPlugin.getWorkspace().getRoot().getContainerForLocation(
        com.unitesk.requality.vscode.ui.runtime.Path.fromOSString(dir.getAbsolutePath()));
    if (!(c instanceof IProject))
      return;
    try {
      build((IProject) c, IncrementalProjectBuilder.AUTO_BUILD, null);
    } catch (CoreException e) {
      RLogger.logError(e);
    }
  }
}
