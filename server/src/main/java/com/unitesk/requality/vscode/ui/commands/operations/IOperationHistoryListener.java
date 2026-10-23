package com.unitesk.requality.vscode.ui.commands.operations;

/** {@code org.eclipse.core.commands.operations.IOperationHistoryListener}. */
@FunctionalInterface
public interface IOperationHistoryListener {
  void historyNotification(OperationHistoryEvent event);
}
