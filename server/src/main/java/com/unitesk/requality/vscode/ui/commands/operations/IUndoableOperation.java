package com.unitesk.requality.vscode.ui.commands.operations;

import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/** {@code org.eclipse.core.commands.operations.IUndoableOperation}. */
public interface IUndoableOperation {
  void addContext(IUndoContext context);

  boolean canExecute();

  boolean canRedo();

  boolean canUndo();

  void dispose();

  IStatus execute(IProgressMonitor monitor, IAdaptable info) throws ExecutionException;

  IUndoContext[] getContexts();

  String getLabel();

  boolean hasContext(IUndoContext context);

  IStatus redo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException;

  void removeContext(IUndoContext context);

  IStatus undo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException;
}
