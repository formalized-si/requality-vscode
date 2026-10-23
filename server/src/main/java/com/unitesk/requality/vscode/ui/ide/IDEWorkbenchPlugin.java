package com.unitesk.requality.vscode.ui.ide;

import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;

/** {@code org.eclipse.ui.internal.ide.IDEWorkbenchPlugin}: the workspace used by the IDE plug-in. */
public final class IDEWorkbenchPlugin {
  private IDEWorkbenchPlugin() {
  }

  /** {@code IDEWorkbenchPlugin.getPluginWorkspace()}: the workspace of the server. */
  public static IWorkspace getPluginWorkspace() {
    return ResourcesPlugin.getWorkspace();
  }
}
