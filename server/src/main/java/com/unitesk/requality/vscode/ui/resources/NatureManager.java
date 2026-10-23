package com.unitesk.requality.vscode.ui.resources;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.core.internal.resources.NatureManager}: when the natures of a project change (project creation
 * with a description, setDescription), the added natures are configured and the removed ones deconfigured, through
 * the nature classes of plugin.xml ({@code org.eclipse.core.resources.natures}, extension id = nature id).
 */
final class NatureManager {
  private NatureManager() {
  }

  /** The nature object of an id, bound to the project, or null when no plug-in contributes it. */
  static IProjectNature create(String natureId, IProject project) {
    for (IConfigurationElement run : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.core.resources.natures")) {
      if (!"runtime".equals(run.getName()) || run.getDeclaringExtension() == null
          || !natureId.equals(run.getDeclaringExtension().getUniqueIdentifier()))
        continue;
      for (IConfigurationElement r : run.getChildren("run")) {
        try {
          IProjectNature n = (IProjectNature) r.createExecutableExtension("class");
          n.setProject(project);
          return n;
        } catch (CoreException | ClassCastException e) {
          RLogger.logError(e);
        }
      }
    }
    return null;
  }

  /** Configures the natures in {@code newIds} that are not in {@code oldIds} and deconfigures the removed ones. */
  static void configureNatures(IProject project, String[] oldIds, String[] newIds) throws CoreException {
    Set<String> before = new LinkedHashSet<>(oldIds == null ? Arrays.asList() : Arrays.asList(oldIds));
    Set<String> after = new LinkedHashSet<>(newIds == null ? Arrays.asList() : Arrays.asList(newIds));
    for (String id : before)
      if (!after.contains(id)) {
        IProjectNature n = create(id, project);
        if (n != null)
          n.deconfigure();
      }
    for (String id : after)
      if (!before.contains(id)) {
        IProjectNature n = create(id, project);
        if (n != null)
          n.configure();
      }
  }
}
