package com.unitesk.requality.vscode.ui.workbench.operations;

import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPartSite;

/** {@code org.eclipse.ui.operations.RedoActionHandler}: Redo of an undo context of the workbench history. */
public final class RedoActionHandler extends OperationHistoryActionHandler {
  public RedoActionHandler(IWorkbenchPartSite site, IUndoContext context) {
    super(site, context);
    setActionDefinitionId("org.eclipse.ui.edit.redo");
  }

  @Override
  void flush() {
    getHistory().dispose(getUndoContext(), false, true, false);
  }

  @Override
  String getCommandString() {
    return "&Redo {0}";
  }

  @Override
  String getTooltipString() {
    return "Redo {0}";
  }

  @Override
  String getSimpleCommandString() {
    return "&Redo";
  }

  @Override
  String getSimpleTooltipString() {
    return "Redo";
  }

  @Override
  IUndoableOperation getOperation() {
    return getHistory().getRedoOperation(getUndoContext());
  }

  @Override
  IStatus runCommand(IProgressMonitor pm) throws ExecutionException {
    return getHistory().redo(getUndoContext(), pm, this);
  }

  @Override
  boolean shouldBeEnabled() {
    return getHistory().canRedo(getUndoContext());
  }
}
