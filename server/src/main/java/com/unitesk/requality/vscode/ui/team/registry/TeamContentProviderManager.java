package com.unitesk.requality.vscode.ui.team.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IExtension;
import com.unitesk.requality.vscode.ui.team.ui.mapping.ITeamContentProviderDescriptor;
import com.unitesk.requality.vscode.ui.team.ui.mapping.ITeamContentProviderManager;

/**
 * {@code org.eclipse.team.internal.ui.registry.TeamContentProviderManager}: the team content providers of the extension
 * point {@code org.eclipse.team.ui.teamContentProviders}, read from the extension registry on first use.
 */
public class TeamContentProviderManager implements ITeamContentProviderManager {
  /** {@code TeamContentProviderManager.PT_TEAM_CONTENT_PROVIDERS} (the point of the team.ui plug-in). */
  public static final String PT_TEAM_CONTENT_PROVIDERS = "org.eclipse.team.ui.teamContentProviders";

  private static TeamContentProviderManager instance;
  private Map<String, ITeamContentProviderDescriptor> descriptors;

  public static synchronized ITeamContentProviderManager getInstance() {
    if (instance == null)
      instance = new TeamContentProviderManager();
    return instance;
  }

  @Override
  public synchronized ITeamContentProviderDescriptor[] getDescriptors() {
    lazyInitialize();
    return descriptors.values().toArray(new ITeamContentProviderDescriptor[0]);
  }

  @Override
  public synchronized ITeamContentProviderDescriptor getDescriptor(String modelProviderId) {
    lazyInitialize();
    return descriptors.get(modelProviderId);
  }

  @Override
  public synchronized void setEnabledDescriptors(ITeamContentProviderDescriptor[] enabled) {
    lazyInitialize();
    for (ITeamContentProviderDescriptor d : descriptors.values())
      ((TeamContentProviderDescriptor) d).setEnabled(false);
    for (ITeamContentProviderDescriptor d : enabled)
      ((TeamContentProviderDescriptor) d).setEnabled(true);
  }

  private void lazyInitialize() {
    if (descriptors != null)
      return;
    descriptors = new LinkedHashMap<>();
    for (IExtension extension : ExtensionRegistry.get().getExtensionPoint(PT_TEAM_CONTENT_PROVIDERS).getExtensions()) {
      try {
        TeamContentProviderDescriptor desc = new TeamContentProviderDescriptor(extension);
        descriptors.put(desc.getModelProviderId(), desc);
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    }
  }
}
