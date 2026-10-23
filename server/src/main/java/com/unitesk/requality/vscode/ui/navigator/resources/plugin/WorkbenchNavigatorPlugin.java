package com.unitesk.requality.vscode.ui.navigator.resources.plugin;

import com.unitesk.requality.vscode.ui.runtime.ILog;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.plugin.WorkbenchNavigatorPlugin}: the plug-in of the resource
 * navigator content (org.eclipse.ui.navigator.resources), its id and its log.
 */
public final class WorkbenchNavigatorPlugin {
  /** The plug-in id. */
  public static final String PLUGIN_ID = "org.eclipse.ui.navigator.resources";

  private static final WorkbenchNavigatorPlugin INSTANCE = new WorkbenchNavigatorPlugin();
  private final ILog log = ILog.of(PLUGIN_ID);

  private WorkbenchNavigatorPlugin() {
  }

  /** {@code WorkbenchNavigatorPlugin.getDefault()}. */
  public static WorkbenchNavigatorPlugin getDefault() {
    return INSTANCE;
  }

  /** {@code Plugin.getLog()}. */
  public ILog getLog() {
    return log;
  }
}
