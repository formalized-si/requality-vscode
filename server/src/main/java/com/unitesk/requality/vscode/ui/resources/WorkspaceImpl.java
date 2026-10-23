package com.unitesk.requality.vscode.ui.resources;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.ISchedulingRule;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.MultiStatus;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Status;

/** The workspace: the folders opened in VS Code. */
final class WorkspaceImpl implements Workspace {
  static final WorkspaceImpl INSTANCE = new WorkspaceImpl();
  private final WorkspaceRoot root = new WorkspaceRoot(this);
  private final List<IResourceChangeListener> listeners = new CopyOnWriteArrayList<>();

  @Override
  public IWorkspaceRoot getRoot() {
    return root;
  }

  @Override
  public void run(IWorkspaceRunnable action, IProgressMonitor monitor) throws CoreException {
    action.run(monitor == null ? new NullProgressMonitor() : monitor);
  }

  @Override
  public void run(IWorkspaceRunnable action, ISchedulingRule rule, int flags, IProgressMonitor monitor)
      throws CoreException {
    run(action, monitor);
  }

  @Override
  public void addResourceChangeListener(IResourceChangeListener listener) {
    listeners.add(listener);
  }

  @Override
  public void addResourceChangeListener(IResourceChangeListener listener, int eventMask) {
    listeners.add(listener);
  }

  @Override
  public void removeResourceChangeListener(IResourceChangeListener listener) {
    listeners.remove(listener);
  }

  /** Delivers a change of the workspace files (reported by the extension's file watcher). */
  void fire(IResourceChangeEvent event) {
    for (IResourceChangeListener l : listeners) {
      try {
        l.resourceChanged(event);
      } catch (RuntimeException e) {
        RLogger.logError(e);
      }
    }
  }

  @Override
  public IResourceRuleFactory getRuleFactory() {
    return new IResourceRuleFactory() {
    };
  }

  @Override
  public IProjectDescription loadProjectDescription(IPath path) throws CoreException {
    java.io.File f = path.toFile();
    if (!f.isFile())
      throw new ResourceException(new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES,
          "Project description file not found: " + f, new java.io.FileNotFoundException(f.getPath())));
    return ProjectDescription.read(f);
  }

  @Override
  public IProjectDescription newProjectDescription(String projectName) {
    ProjectDescription d = new ProjectDescription();
    d.setName(projectName);
    return d;
  }

  private com.unitesk.requality.vscode.ui.resources.team.FileModificationValidator validator;
  private boolean validatorInitialized;

  /**
   * {@code Workspace.initializeValidator}: the validator contributed to
   * {@code org.eclipse.core.resources.fileModificationValidator} (the team core's), if any.
   */
  private synchronized com.unitesk.requality.vscode.ui.resources.team.FileModificationValidator validator() {
    if (!validatorInitialized) {
      validatorInitialized = true;
      for (com.unitesk.requality.vscode.ui.runtime.IConfigurationElement e : com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry
          .get().getConfigurationElementsFor(ResourcesPlugin.PI_RESOURCES, "fileModificationValidator")) {
        try {
          Object o = e.createExecutableExtension("class");
          if (o instanceof com.unitesk.requality.vscode.ui.resources.team.FileModificationValidator v) {
            validator = v;
            break;
          }
        } catch (com.unitesk.requality.vscode.ui.runtime.CoreException ex) {
          com.unitesk.requality.core.log.RLogger.logError(ex);
        }
      }
    }
    return validator;
  }

  /**
   * {@code Workspace.validateEdit}: the file modification validator decides (the team core's, which asks the user to
   * make read-only files writable); without a validator the read-only files are reported with the status
   * "Resource '...' is read-only." under "Cannot edit read-only resources.".
   */
  @Override
  public IStatus validateEdit(IFile[] files, Object context) {
    com.unitesk.requality.vscode.ui.resources.team.FileModificationValidator v = validator();
    if (v != null) {
      com.unitesk.requality.vscode.ui.resources.team.FileModificationValidationContext validationContext;
      if (context == null)
        validationContext = null;
      else if (context instanceof com.unitesk.requality.vscode.ui.resources.team.FileModificationValidationContext c)
        validationContext = c;
      else
        validationContext = new com.unitesk.requality.vscode.ui.resources.team.FileModificationValidationContext(context);
      IStatus status = v.validateEdit(files, validationContext);
      return status == null ? Status.OK_STATUS : status;
    }
    // READ_ONLY_LOCAL: the code of {@code IResourceStatus.READ_ONLY_LOCAL}
    MultiStatus result = new MultiStatus(ResourcesPlugin.PI_RESOURCES, 279, "Cannot edit read-only resources.", null);
    for (IFile file : files) {
      if (file.isReadOnly()) {
        result.add(new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES,
            "Resource '" + file.getFullPath() + "' is read-only."));
      }
    }
    return result.getChildren().length == 0 ? Status.OK_STATUS : result;
  }

  @Override
  public IStatus validateName(String segment, int typeMask) {
    if (segment == null || segment.trim().isEmpty())
      return new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES, "Name must not be empty");
    for (char c : segment.toCharArray())
      if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|')
        return new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES, c + " is an invalid character in resource name '" + segment + "'.");
    if (segment.endsWith(".") || segment.endsWith(" "))
      return new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES, "Resource name cannot end with a dot or space");
    return Status.OK_STATUS;
  }

  /**
   * Workspace.validateProjectLocation: a project location must be absolute and must not overlap (contain or be inside)
   * the location of another project.
   */
  @Override
  public IStatus validateProjectLocation(IProject project, IPath location) {
    if (location == null)
      return Status.OK_STATUS;
    java.io.File f = location.toFile();
    if (!f.isAbsolute())
      return new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES, location + " is not an absolute path.");
    java.nio.file.Path p = canonical(f);
    // the project itself may keep its location, but cannot go inside it
    if (project != null && project.getLocation() != null) {
      java.nio.file.Path own = canonical(project.getLocation().toFile());
      if (!p.equals(own) && p.startsWith(own))
        return new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES,
            location.toOSString() + " overlaps the location of another project: '" + project.getName() + "'");
    }
    for (IProject other : root.getProjects()) {
      if (other == null || other.equals(project) || !other.exists() || other.getLocation() == null)
        continue;
      java.nio.file.Path o = canonical(other.getLocation().toFile());
      if (p.startsWith(o) || o.startsWith(p))
        return new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES,
            location.toOSString() + " overlaps the location of another project: '" + other.getName() + "'");
    }
    return Status.OK_STATUS;
  }

  /** Project > Build Automatically (kept with the workspace state, on by default as in Eclipse). */
  private static java.nio.file.Path canonical(java.io.File f) {
    try {
      return f.getCanonicalFile().toPath();
    } catch (java.io.IOException e) {
      return f.getAbsoluteFile().toPath().normalize();
    }
  }

  @Override
  public IWorkspaceDescription getDescription() {
    return new WorkspaceDescription();
  }

  @Override
  public void setDescription(IWorkspaceDescription description) throws CoreException {
    // The settings of the description are stored as they are set (see WorkspaceDescription).
    if (description == null)
      throw new CoreException(new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES, "The description is null"));
  }

  @Override
  public boolean isAutoBuilding() {
    return com.unitesk.requality.vscode.core.ServerContext.server() == null
        || com.unitesk.requality.vscode.core.ServerContext.server().workspace().isAutoBuilding();
  }
}
