package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.PlatformUI}. */
public final class PlatformUI {
  public static final String PLUGIN_ID = "org.eclipse.ui";

  private PlatformUI() {
  }

  public static IWorkbench getWorkbench() {
    return Workbench.INSTANCE;
  }

  public static boolean isWorkbenchRunning() {
    return true;
  }

  /** {@code org.eclipse.ui.PlatformUI.getPreferenceStore()}: the preferences of the workbench (the plug-in store). */
  public static com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore getPreferenceStore() {
    return com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore.getPlugin();
  }
}
