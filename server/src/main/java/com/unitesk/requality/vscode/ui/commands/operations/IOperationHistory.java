package com.unitesk.requality.vscode.ui.commands.operations;

import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/** {@code org.eclipse.core.commands.operations.IOperationHistory}. */
public interface IOperationHistory {
  IUndoContext GLOBAL_UNDO_CONTEXT = new ObjectUndoContext("global", "Global Undo Context");

  void add(IUndoableOperation operation);

  void addOperationHistoryListener(IOperationHistoryListener listener);

  void removeOperationHistoryListener(IOperationHistoryListener listener);

  boolean canRedo(IUndoContext context);

  boolean canUndo(IUndoContext context);

  void dispose(IUndoContext context, boolean flushUndo, boolean flushRedo, boolean flushContext);

  IStatus execute(IUndoableOperation operation, IProgressMonitor monitor, IAdaptable info) throws ExecutionException;

  IUndoableOperation[] getRedoHistory(IUndoContext context);

  IUndoableOperation getRedoOperation(IUndoContext context);

  IUndoableOperation[] getUndoHistory(IUndoContext context);

  IUndoableOperation getUndoOperation(IUndoContext context);

  IStatus redo(IUndoContext context, IProgressMonitor monitor, IAdaptable info) throws ExecutionException;

  IStatus redoOperation(IUndoableOperation operation, IProgressMonitor monitor, IAdaptable info)
      throws ExecutionException;

  IStatus undo(IUndoContext context, IProgressMonitor monitor, IAdaptable info) throws ExecutionException;

  IStatus undoOperation(IUndoableOperation operation, IProgressMonitor monitor, IAdaptable info)
      throws ExecutionException;
}
