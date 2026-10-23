package com.unitesk.requality.vscode.ui.commands;

/** {@code org.eclipse.core.commands.IHandler}. */
public interface IHandler {
  Object execute(ExecutionEvent event) throws ExecutionException;

  boolean isEnabled();

  boolean isHandled();

  void addHandlerListener(IHandlerListener handlerListener);

  void removeHandlerListener(IHandlerListener handlerListener);

  void dispose();
}
