package com.unitesk.requality.eclipse.operations;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.core.transaction.UndoableTreeDBOperation;
import com.unitesk.requality.eclipse.core.EclipseProgressMonitor;

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
