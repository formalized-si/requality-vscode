package com.unitesk.requality.vscode.ui.workbench.operations;

import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPartSite;

/** {@code org.eclipse.ui.operations.UndoActionHandler}: Undo of an undo context of the workbench history. */
public final class UndoActionHandler extends OperationHistoryActionHandler {
  public UndoActionHandler(IWorkbenchPartSite site, IUndoContext context) {
    super(site, context);
    setActionDefinitionId("org.eclipse.ui.edit.undo");
  }

  @Override
  void flush() {
    getHistory().dispose(getUndoContext(), true, false, false);
  }

  @Override
  String getCommandString() {
    return "&Undo {0}";
  }

  @Override
  String getTooltipString() {
    return "Undo {0}";
  }

  @Override
  String getSimpleCommandString() {
    return "&Undo";
  }

  @Override
  String getSimpleTooltipString() {
    return "Undo";
  }

  @Override
  IUndoableOperation getOperation() {
    return getHistory().getUndoOperation(getUndoContext());
  }

  @Override
  IStatus runCommand(IProgressMonitor pm) throws ExecutionException {
    return getHistory().undo(getUndoContext(), pm, this);
  }

  @Override
  boolean shouldBeEnabled() {
    return getHistory().canUndo(getUndoContext());
  }
}
