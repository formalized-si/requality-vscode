package com.unitesk.requality.core.operation.undoredo;

import java.util.List;

import com.unitesk.requality.core.operation.LocalListUndoRedoOperator;
import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.core.transaction.UndoableTreeDBOperation;

public class UndoRedoMech {

  public static boolean callUndo() {
    return op.callUndo();
  }
  public static boolean callRedo() {
    return op.callRedo();
  }
  static IUndoRedoOperator op = new LocalListUndoRedoOperator();
  public static void setOperator(IUndoRedoOperator r){
    op = r;
  }
  public static IUndoRedoOperator getOperator(){
    return op;
  }
  public static List<UndoableTreeDBOperation> getRedoList() {
    return op.getRedoList();
  }
  public static List<UndoableTreeDBOperation> getUndoList() {
    return op.getUndoList();
  }
  public static void callReset() {
    op.callReset();
  }
  public static void registerOperation(TransactionStorage tStorage) {
    op.registerOperation(tStorage);
  }
}
