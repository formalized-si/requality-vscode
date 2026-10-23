package com.unitesk.requality.vscode.ui.team;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

/**
 * {@code org.eclipse.team.core.RepositoryProvider}: the team provider a project is shared with. A project is mapped to
 * a provider by a team plug-in ({@link #map}) and only to a provider contributed to
 * {@code org.eclipse.team.core.repository}; the Requality product contributes none (EGit, Subversive... are separate
 * plug-ins), so, as in an Eclipse installation of Requality alone, no project is shared.
 */
public abstract class RepositoryProvider {
  private static final String REPOSITORY = "org.eclipse.team.core.repository";
  private static final Map<IProject, String> MAPPED = new ConcurrentHashMap<>();
  private final IProject project;

  protected RepositoryProvider(IProject project) {
    this.project = project;
  }

  public abstract String getID();

  public IProject getProject() {
    return project;
  }

  /** {@code RepositoryProvider.deconfigure}: the project is no longer shared (nothing to undo for the own providers). */
  public void deconfigure() throws CoreException {
    unmap(project);
  }

  private static final class Mapped extends RepositoryProvider {
    private final String id;

    Mapped(IProject project, String id) {
      super(project);
      this.id = id;
    }

    @Override
    public String getID() {
      return id;
    }
  }

  /** The repository provider ids contributed by the installed team plug-ins. */
  static boolean isContributed(String id) {
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor(REPOSITORY))
      if ("repository".equals(e.getName()) && id.equals(e.getAttribute("id")))
        return true;
    return false;
  }

  /** Shares the project with the provider (a team plug-in's Share Project). */
  public static void map(IProject project, String id) throws CoreException {
    if (!isContributed(id))
      throw new CoreException(new Status(IStatus.ERROR, "org.eclipse.team.core",
          "Error associating " + project.getName() + " with provider id " + id + ": the provider is not installed."));
    MAPPED.put(project, id);
  }

  public static void unmap(IProject project) {
    MAPPED.remove(project);
  }

  /** The provider the project is shared with, or null. */
  public static RepositoryProvider getProvider(IProject project) {
    if (project == null || !project.isAccessible())
      return null;
    String id = MAPPED.get(project);
    return id == null || !isContributed(id) ? null : new Mapped(project, id);
  }

  /** The provider of the project if it has the id, else null. */
  public static RepositoryProvider getProvider(IProject project, String id) {
    RepositoryProvider p = getProvider(project);
    return p != null && p.getID().equals(id) ? p : null;
  }

  public static boolean isShared(IProject project) {
    return getProvider(project) != null;
  }
}
