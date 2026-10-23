package com.unitesk.requality.vscode.ui.team.ui.mapping;

/**
 * {@code org.eclipse.team.ui.mapping.ITeamContentProviderDescriptor}: a team content provider of the extension point
 * {@code org.eclipse.team.ui.teamContentProviders}: the model provider that is synchronized, and whether it is enabled.
 */
public interface ITeamContentProviderDescriptor {
  /** The id of the navigator content extension that shows the model. */
  String getContentExtensionId();

  /** The id of the model provider the content provider shows. */
  String getModelProviderId();

  /** The name of the contribution (its extension label), or null when it has none. */
  String getName();

  /** Whether the model is enabled (the default). */
  boolean isEnabled();
}
