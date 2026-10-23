package com.unitesk.requality.core.operation;

import java.util.LinkedList;
import java.util.List;

import com.unitesk.requality.core.operation.undoredo.IUndoRedoOperator;
import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.core.transaction.UndoableTreeDBOperation;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public class LocalListUndoRedoOperator implements IUndoRedoOperator {
  private LinkedList<UndoableTreeDBOperation> undoTransactionList = new LinkedList<>(), 
                                              redoTransactionList = new LinkedList<>();
  private IProgressMonitor monitor = ConsoleProgressMonitor.ONE;
  @Override
  public boolean callUndo() {
    if(undoTransactionList.isEmpty())
      return false;
    UndoableTreeDBOperation op = undoTransactionList.removeLast();
    redoTransactionList.addLast(op);
    return op.undo(monitor);
  }

  @Override
  public boolean callRedo() {
    if(redoTransactionList.isEmpty())
      return false;
    UndoableTreeDBOperation op = redoTransactionList.removeLast();
    undoTransactionList.addLast(op);
    return op.redo(monitor);
  }

  @Override
  public List<UndoableTreeDBOperation> getUndoList() {
    return undoTransactionList;
  }

  @Override
  public List<UndoableTreeDBOperation> getRedoList() {
    return redoTransactionList;
  }

  @Override
  public void callReset() {
    undoTransactionList.clear();
    redoTransactionList.clear();
  }

  @Override
  public void registerOperation(TransactionStorage tStorage) {
    undoTransactionList.add(new UndoableTreeDBOperation(tStorage));
    if(undoTransactionList.size()>10)
      undoTransactionList.removeFirst();
    redoTransactionList.clear();//TODO test
  }
}
