package com.unitesk.requality.vscode.ui.e4.core.services.log;

/**
 * {@code org.eclipse.e4.core.services.log.Logger}: the workbench logger the plug-in logs its errors and infos to (the
 * Activator asks the workbench for it). The server provides it, see
 * {@code com.unitesk.requality.vscode.ui.workbench.Workbench}.
 */
public interface Logger {
  void info(String message);

  void error(String message);
}
