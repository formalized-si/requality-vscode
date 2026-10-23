package com.unitesk.requality.vscode.ui.resources;

/** {@code org.eclipse.core.resources.ResourcesPlugin}. */
public final class ResourcesPlugin {
  public static final String PI_RESOURCES = "org.eclipse.core.resources";

  private ResourcesPlugin() {
  }

  public static IWorkspace getWorkspace() {
    return WorkspaceImpl.INSTANCE;
  }

  /** The default charset of the text files of the workspace (Workspace preferences: Text file encoding). */
  public static String getEncoding() {
    return WorkspaceImpl.INSTANCE.getDescription().getEncoding();
  }
}
