package com.unitesk.requality.vscode.ui.commands;

/** {@code org.eclipse.ui.handlers.IHandlerActivation}. */
public interface IHandlerActivation {
  String getCommandId();

  IHandler getHandler();
}
