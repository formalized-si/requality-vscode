package com.unitesk.requality.core.transaction;

public interface ITransactionListener {
  void execute(TransactionStorage tStorage);

  void redo(TransactionStorage tStorage);

  void undo(TransactionStorage tStorage);

  void _dispose();
}
