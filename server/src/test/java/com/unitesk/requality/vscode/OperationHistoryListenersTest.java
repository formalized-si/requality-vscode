package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.unitesk.requality.vscode.ui.commands.operations.IOperationHistoryListener;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation;
import com.unitesk.requality.vscode.ui.commands.operations.OperationHistory;
import com.unitesk.requality.vscode.ui.commands.operations.OperationHistoryEvent;

/**
 * The operation history does not register again a listener equivalent to a registered one (same anonymous class, same
 * captured state), as TreeNodePropertiesTab registers one on each input change; listeners with their own state stay.
 */
class OperationHistoryListenersTest {
  final AtomicInteger refreshes = new AtomicInteger();

  /** An anonymous listener like the one of TreeNodePropertiesTab (it captures only this object). */
  IOperationHistoryListener refreshListener() {
    return new IOperationHistoryListener() {
      @Override
      public void historyNotification(OperationHistoryEvent event) {
        if (event.getEventType() == OperationHistoryEvent.OPERATION_ADDED)
          refreshes.incrementAndGet();
      }
    };
  }

  @Test
  void equivalentListenersAreRegisteredOnce() throws Exception {
    OperationHistory history = OperationHistory.INSTANCE;
    IOperationHistoryListener a = refreshListener();
    IOperationHistoryListener b = refreshListener();
    AtomicInteger own = new AtomicInteger();
    IOperationHistoryListener c = event -> {
      if (event.getEventType() == OperationHistoryEvent.OPERATION_ADDED)
        own.incrementAndGet();
    };
    history.addOperationHistoryListener(a);
    history.addOperationHistoryListener(b);
    history.addOperationHistoryListener(c);
    try {
      IUndoableOperation op = new com.unitesk.requality.vscode.ui.commands.operations.AbstractOperation("test") {
        @Override
        public com.unitesk.requality.vscode.ui.runtime.IStatus execute(com.unitesk.requality.vscode.ui.runtime.IProgressMonitor m,
            com.unitesk.requality.vscode.ui.runtime.IAdaptable info) {
          return com.unitesk.requality.vscode.ui.runtime.Status.OK_STATUS;
        }

        @Override
        public com.unitesk.requality.vscode.ui.runtime.IStatus undo(com.unitesk.requality.vscode.ui.runtime.IProgressMonitor m,
            com.unitesk.requality.vscode.ui.runtime.IAdaptable info) {
          return com.unitesk.requality.vscode.ui.runtime.Status.OK_STATUS;
        }

        @Override
        public com.unitesk.requality.vscode.ui.runtime.IStatus redo(com.unitesk.requality.vscode.ui.runtime.IProgressMonitor m,
            com.unitesk.requality.vscode.ui.runtime.IAdaptable info) {
          return com.unitesk.requality.vscode.ui.runtime.Status.OK_STATUS;
        }
      };
      op.addContext(com.unitesk.requality.vscode.ui.commands.operations.IOperationHistory.GLOBAL_UNDO_CONTEXT);
      history.add(op);
      assertEquals(1, refreshes.get(), "the equivalent listener is notified once");
      assertEquals(1, own.get());
    } finally {
      history.removeOperationHistoryListener(a);
      history.removeOperationHistoryListener(b);
      history.removeOperationHistoryListener(c);
    }
  }
}
