package com.unitesk.requality.vscode.ui.commands;

/** {@code org.eclipse.core.commands.IHandlerListener}. */
@FunctionalInterface
public interface IHandlerListener {
  void handlerChanged(HandlerEvent handlerEvent);
}
