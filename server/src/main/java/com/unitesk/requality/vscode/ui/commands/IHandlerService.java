package com.unitesk.requality.vscode.ui.commands;

import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.ui.handlers.IHandlerService}. */
public interface IHandlerService {
  Object executeCommand(String commandId, Event event)
      throws ExecutionException, NotDefinedException, NotEnabledException, NotHandledException;

  IHandlerActivation activateHandler(String commandId, IHandler handler);

  void deactivateHandler(IHandlerActivation activation);

  IEvaluationContext getCurrentState();

  IEvaluationContext createContextSnapshot(boolean includeSelection);
}
