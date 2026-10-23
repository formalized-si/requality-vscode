package com.unitesk.requality.vscode.port.eclipse.operations;

import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.operations.AbstractOperation;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.core.transaction.UndoableTreeDBOperation;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseProgressMonitor;

public class UndoableEclipseTreeDBOperation extends AbstractOperation {
  private UndoableTreeDBOperation fallback;

  public static UndoableEclipseTreeDBOperation getOne(TransactionStorage ts) {
    return new UndoableEclipseTreeDBOperation(new UndoableTreeDBOperation(ts));
  }
  
  private UndoableEclipseTreeDBOperation(UndoableTreeDBOperation op) {
    super(op.getName());
    this.fallback = op;
  }

  @Override
  public IStatus execute(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
    return Status.CANCEL_STATUS;
  }

  @Override
  public boolean canExecute() {
    return false;
  }

  @Override
  public IStatus redo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
    fallback.redo(EclipseProgressMonitor.from(monitor));
    return Status.OK_STATUS;
  }

  @Override
  public IStatus undo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
    fallback.undo(EclipseProgressMonitor.from(monitor));
    return Status.OK_STATUS;
  }

  @Override
  public void dispose() {
    fallback.dispose();
  }

  public UndoableTreeDBOperation getCoreOperation() {
   return fallback;
  }
}
