package com.unitesk.requality.vscode.ui.team.ui.mapping;

/** {@code org.eclipse.team.ui.mapping.ITeamContentProviderManager}: the team content providers of the platform. */
public interface ITeamContentProviderManager {
  /** The descriptors of all team content providers. */
  ITeamContentProviderDescriptor[] getDescriptors();

  /** The descriptor of the model provider with the id, or null. */
  ITeamContentProviderDescriptor getDescriptor(String modelProviderId);

  /** Enables exactly the given descriptors; the others are disabled. */
  void setEnabledDescriptors(ITeamContentProviderDescriptor[] descriptors);
}
