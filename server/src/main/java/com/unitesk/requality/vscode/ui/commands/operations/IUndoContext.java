package com.unitesk.requality.vscode.ui.commands.operations;

/** {@code org.eclipse.core.commands.operations.IUndoContext}. */
public interface IUndoContext {
  String getLabel();

  boolean matches(IUndoContext context);
}
