package com.unitesk.requality.vscode.ui.team;

import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.team.core.RepositoryProviderType}: a repository type of the {@code org.eclipse.team.core.repository}
 * extension point, the one a project set entry names. No provider types are registered by the Requality product, so
 * {@link #getProviderType} finds none unless a team plug-in contributes one.
 * VSCODE: the project set capability of a provider type is not ported, so a found type adds no projects.
 */
public class RepositoryProviderType {
  private static final String REPOSITORY = "org.eclipse.team.core.repository";

  private final String id;

  private RepositoryProviderType(String id) {
    this.id = id;
  }

  /** {@code RepositoryProviderType.getProviderType}: the provider type with the id, or null. */
  public static RepositoryProviderType getProviderType(String id) {
    if (id == null)
      return null;
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor(REPOSITORY))
      if ("repository".equals(e.getName()) && id.equals(e.getAttribute("id")))
        return new RepositoryProviderType(id);
    return null;
  }

  /** The id of the provider type. */
  public String getID() {
    return id;
  }
}
