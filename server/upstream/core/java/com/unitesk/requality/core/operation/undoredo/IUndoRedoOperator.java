package com.unitesk.requality.core.operation.undoredo;

import java.util.List;

import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.core.transaction.UndoableTreeDBOperation;

public interface IUndoRedoOperator {

  boolean callUndo();

  boolean callRedo();

  List<UndoableTreeDBOperation> getUndoList();
  List<UndoableTreeDBOperation> getRedoList();

  void callReset();

  void registerOperation(TransactionStorage tStorage);
}
