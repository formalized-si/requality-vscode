package com.unitesk.requality.vscode.ui.team.registry;

import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.IExtension;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;
import com.unitesk.requality.vscode.ui.team.ui.mapping.ITeamContentProviderDescriptor;

/**
 * {@code org.eclipse.team.internal.ui.registry.TeamContentProviderDescriptor}: a {@code teamContentProvider} of the
 * extension point {@code org.eclipse.team.ui.teamContentProviders}. Its enablement is kept in the Team UI preferences
 * under the model provider id, as Eclipse keeps it in the instance preferences.
 * VSCODE: the icon and the preference page of the content provider are not read (the Models page has no images).
 */
public class TeamContentProviderDescriptor implements ITeamContentProviderDescriptor {
  private static final String TAG_TEAM_CONTENT_PROVIDER = "teamContentProvider";
  private static final String ATT_MODEL_PROVIDER_ID = "modelProviderId";
  private static final String ATT_CONTENT_EXTENSION_ID = "contentExtensionId";
  private static final String ATT_SUPPORTS_FLAT_LAYOUT = "supportsFlatLayout";
  /** The preference key prefix of the descriptors ({@code TeamContentProviderDescriptor.PREF_TEAM_CONTENT_DESCRIPTORS}). */
  static final String PREF_TEAM_CONTENT_DESCRIPTORS = "teamContentDescriptors.";
  /** The preference key suffix of the enablement ({@code TeamContentProviderDescriptor.PREF_ENABLED}). */
  static final String PREF_ENABLED = ".enabled";

  private final String modelProviderId;
  private final String contentExtensionId;
  private final String name;
  private final boolean supportsFlatLayout;

  public TeamContentProviderDescriptor(IExtension extension) throws CoreException {
    String id = extension.getUniqueIdentifier();
    IConfigurationElement[] elements = extension.getConfigurationElements();
    // there has to be exactly one team content provider element in the extension (see teamContentProviders.exsd)
    if (elements.length != 1)
      fail("The extension " + (id == null ? "" : id) + " must have exactly one " + TAG_TEAM_CONTENT_PROVIDER + " element.");
    IConfigurationElement element = elements[0];
    if (!TAG_TEAM_CONTENT_PROVIDER.equalsIgnoreCase(element.getName()))
      fail("The extension " + (id == null ? "" : id) + " must have exactly one " + TAG_TEAM_CONTENT_PROVIDER + " element.");
    modelProviderId = element.getAttribute(ATT_MODEL_PROVIDER_ID);
    contentExtensionId = element.getAttribute(ATT_CONTENT_EXTENSION_ID);
    String flat = element.getAttribute(ATT_SUPPORTS_FLAT_LAYOUT);
    supportsFlatLayout = flat != null && Boolean.parseBoolean(flat);
    name = extension.getLabel();
    if (modelProviderId == null)
      fail("The attribute " + ATT_MODEL_PROVIDER_ID + " of " + TAG_TEAM_CONTENT_PROVIDER + " is required.");
    if (contentExtensionId == null)
      fail("The attribute " + ATT_CONTENT_EXTENSION_ID + " of " + TAG_TEAM_CONTENT_PROVIDER + " is required.");
  }

  private static void fail(String reason) throws CoreException {
    throw new CoreException(new Status(IStatus.ERROR, "org.eclipse.team.ui", 0, reason, null));
  }

  @Override
  public String getContentExtensionId() {
    return contentExtensionId;
  }

  @Override
  public String getModelProviderId() {
    return modelProviderId;
  }

  @Override
  public String getName() {
    return name;
  }

  /** Whether the content provider shows its model in a flat layout (the compressed layout is the default). */
  public boolean supportsFlatLayout() {
    return supportsFlatLayout;
  }

  /** The key of the enablement of the descriptor in the Team UI preferences. */
  private String enabledKey() {
    return PREF_TEAM_CONTENT_DESCRIPTORS + modelProviderId + PREF_ENABLED;
  }

  @Override
  public boolean isEnabled() {
    IPreferenceStore store = TeamUIPlugin.getPreferenceStore();
    return !store.contains(enabledKey()) || store.getBoolean(enabledKey());
  }

  /** Sets the enablement of the descriptor ({@code TeamContentProviderDescriptor.setEnabled}). */
  public void setEnabled(boolean enable) {
    if (isEnabled() != enable)
      TeamUIPlugin.getPreferenceStore().setValue(enabledKey(), enable);
  }

  /**
   * The label of the model of the descriptor when the contribution has no name: the label of its model provider.
   * VSCODE: the model providers are not contributed to the registry; the labels of the Team models are the ones of the
   * team.ui plug-in ({@code PatchModelProvider} of team-plugin.properties) and of the resource model.
   */
  public static String modelLabel(String modelProviderId) {
    if ("org.eclipse.team.ui.patchModelProvider".equals(modelProviderId))
      return "Patch Contents";
    if ("org.eclipse.core.resources.modelProvider".equals(modelProviderId))
      return "Workspace";
    return modelProviderId;
  }
}
