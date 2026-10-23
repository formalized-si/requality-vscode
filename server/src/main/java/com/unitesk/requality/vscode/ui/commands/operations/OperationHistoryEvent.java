package com.unitesk.requality.vscode.ui.commands.operations;

/** {@code org.eclipse.core.commands.operations.OperationHistoryEvent}. */
public final class OperationHistoryEvent {
  public static final int ABOUT_TO_EXECUTE = 1;
  public static final int ABOUT_TO_REDO = 2;
  public static final int ABOUT_TO_UNDO = 3;
  public static final int DONE = 4;
  public static final int OPERATION_ADDED = 5;
  public static final int OPERATION_CHANGED = 6;
  public static final int OPERATION_NOT_OK = 7;
  public static final int OPERATION_REMOVED = 8;
  public static final int REDONE = 9;
  public static final int UNDONE = 10;

  private final int type;
  private final IOperationHistory history;
  private final IUndoableOperation operation;

  public OperationHistoryEvent(int type, IOperationHistory history, IUndoableOperation operation) {
    this.type = type;
    this.history = history;
    this.operation = operation;
  }

  public int getEventType() {
    return type;
  }

  public IOperationHistory getHistory() {
    return history;
  }

  public IUndoableOperation getOperation() {
    return operation;
  }
}
