package com.unitesk.requality.vscode.ui.commands;

import java.util.Map;

/** {@code org.eclipse.ui.commands.ICommandService}. */
public interface ICommandService {
  Command getCommand(String commandId);

  @SuppressWarnings("rawtypes")
  void refreshElements(String commandId, Map filter);
}
