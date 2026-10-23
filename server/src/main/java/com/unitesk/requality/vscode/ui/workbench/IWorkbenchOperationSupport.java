package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.commands.operations.IOperationHistory;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;

/** {@code org.eclipse.ui.operations.IWorkbenchOperationSupport}. */
public interface IWorkbenchOperationSupport {
  IUndoContext getUndoContext();

  IOperationHistory getOperationHistory();
}
