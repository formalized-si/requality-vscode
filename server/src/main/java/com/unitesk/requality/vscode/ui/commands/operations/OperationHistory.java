package com.unitesk.requality.vscode.ui.commands.operations;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Status;

/**
 * {@code org.eclipse.core.commands.operations.DefaultOperationHistory}: undo and redo stacks of operations with
 * contexts. The workbench undo context keeps the Eclipse default limit of 25 operations.
 */
public final class OperationHistory implements IOperationHistory {
  public static final OperationHistory INSTANCE = new OperationHistory();
  public static final int DEFAULT_LIMIT = 25;
  private final LinkedList<IUndoableOperation> undoList = new LinkedList<>();
  private final LinkedList<IUndoableOperation> redoList = new LinkedList<>();
  private final List<IOperationHistoryListener> listeners = new CopyOnWriteArrayList<>();
  private int limit = DEFAULT_LIMIT;

  private OperationHistory() {
  }

  public synchronized void setLimit(IUndoContext context, int limit) {
    this.limit = limit;
    trim();
  }

  public synchronized int getLimit(IUndoContext context) {
    return limit;
  }

  private void trim() {
    while (undoList.size() > limit)
      undoList.removeFirst().dispose();
    while (redoList.size() > limit)
      redoList.removeFirst().dispose();
  }

  private void fire(int type, IUndoableOperation op) {
    OperationHistoryEvent e = new OperationHistoryEvent(type, this, op);
    for (IOperationHistoryListener l : listeners) {
      try {
        l.historyNotification(e);
      } catch (RuntimeException ex) {
        RLogger.logError(ex);
      }
    }
  }

  @Override
  public void add(IUndoableOperation operation) {
    List<IUndoableOperation> flushed;
    synchronized (this) {
      undoList.addLast(operation);
      flushed = new ArrayList<>(redoList);
      redoList.clear();
      trim();
    }
    for (IUndoableOperation o : flushed)
      o.dispose();
    fire(OperationHistoryEvent.OPERATION_ADDED, operation);
  }

  @Override
  public void addOperationHistoryListener(IOperationHistoryListener listener) {
    if (!listeners.contains(listener) && !hasEquivalent(listener))
      listeners.add(listener);
  }

  /**
   * VSCODE: a listener of the same anonymous or local class with the same captured state (outer instance and captured
   * variables) as a registered one behaves exactly like it, so it is not registered twice. Code that registers such a
   * listener again on each input change (TreeNodePropertiesTab) would otherwise multiply the notifications.
   */
  private boolean hasEquivalent(IOperationHistoryListener listener) {
    Class<?> c = listener.getClass();
    if (!(c.isAnonymousClass() || c.isLocalClass() || c.isSynthetic()))
      return false;
    for (IOperationHistoryListener l : listeners)
      if (l.getClass() == c && sameCapturedState(l, listener))
        return true;
    return false;
  }

  private static boolean sameCapturedState(Object a, Object b) {
    for (java.lang.reflect.Field f : a.getClass().getDeclaredFields()) {
      if (java.lang.reflect.Modifier.isStatic(f.getModifiers()))
        continue;
      if (!f.isSynthetic())
        return false; // a listener with its own state is kept
      try {
        f.setAccessible(true);
        if (f.get(a) != f.get(b))
          return false;
      } catch (ReflectiveOperationException | RuntimeException e) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void removeOperationHistoryListener(IOperationHistoryListener listener) {
    listeners.remove(listener);
  }

  private static boolean matches(IUndoableOperation op, IUndoContext context) {
    return context == null || context == GLOBAL_UNDO_CONTEXT || op.hasContext(context);
  }

  private synchronized IUndoableOperation last(LinkedList<IUndoableOperation> list, IUndoContext context) {
    for (int i = list.size() - 1; i >= 0; i--)
      if (matches(list.get(i), context))
        return list.get(i);
    return null;
  }

  @Override
  public boolean canRedo(IUndoContext context) {
    IUndoableOperation op = getRedoOperation(context);
    return op != null && op.canRedo();
  }

  @Override
  public boolean canUndo(IUndoContext context) {
    IUndoableOperation op = getUndoOperation(context);
    return op != null && op.canUndo();
  }

  @Override
  public void dispose(IUndoContext context, boolean flushUndo, boolean flushRedo, boolean flushContext) {
    List<IUndoableOperation> removed = new ArrayList<>();
    synchronized (this) {
      if (flushUndo)
        for (IUndoableOperation op : new ArrayList<>(undoList))
          if (matches(op, context)) {
            undoList.remove(op);
            removed.add(op);
          }
      if (flushRedo)
        for (IUndoableOperation op : new ArrayList<>(redoList))
          if (matches(op, context)) {
            redoList.remove(op);
            removed.add(op);
          }
    }
    for (IUndoableOperation op : removed) {
      op.dispose();
      fire(OperationHistoryEvent.OPERATION_REMOVED, op);
    }
  }

  @Override
  public IStatus execute(IUndoableOperation operation, IProgressMonitor monitor, IAdaptable info)
      throws ExecutionException {
    if (!operation.canExecute())
      return Status.CANCEL_STATUS;
    fire(OperationHistoryEvent.ABOUT_TO_EXECUTE, operation);
    IStatus s = operation.execute(monitor == null ? new NullProgressMonitor() : monitor, info);
    if (s.isOK()) {
      fire(OperationHistoryEvent.DONE, operation);
      add(operation);
    } else {
      fire(OperationHistoryEvent.OPERATION_NOT_OK, operation);
    }
    return s;
  }

  @Override
  public synchronized IUndoableOperation[] getRedoHistory(IUndoContext context) {
    List<IUndoableOperation> r = new ArrayList<>();
    for (IUndoableOperation op : redoList)
      if (matches(op, context))
        r.add(op);
    return r.toArray(new IUndoableOperation[0]);
  }

  @Override
  public IUndoableOperation getRedoOperation(IUndoContext context) {
    return last(redoList, context);
  }

  @Override
  public synchronized IUndoableOperation[] getUndoHistory(IUndoContext context) {
    List<IUndoableOperation> r = new ArrayList<>();
    for (IUndoableOperation op : undoList)
      if (matches(op, context))
        r.add(op);
    return r.toArray(new IUndoableOperation[0]);
  }

  @Override
  public IUndoableOperation getUndoOperation(IUndoContext context) {
    return last(undoList, context);
  }

  @Override
  public IStatus redo(IUndoContext context, IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
    IUndoableOperation op = getRedoOperation(context);
    return op == null ? Status.CANCEL_STATUS : redoOperation(op, monitor, info);
  }

  @Override
  public IStatus redoOperation(IUndoableOperation operation, IProgressMonitor monitor, IAdaptable info)
      throws ExecutionException {
    if (!operation.canRedo())
      return Status.CANCEL_STATUS;
    fire(OperationHistoryEvent.ABOUT_TO_REDO, operation);
    IStatus s = operation.redo(monitor == null ? new NullProgressMonitor() : monitor, info);
    if (s.isOK()) {
      synchronized (this) {
        redoList.remove(operation);
        undoList.addLast(operation);
        trim();
      }
      fire(OperationHistoryEvent.REDONE, operation);
    } else {
      fire(OperationHistoryEvent.OPERATION_NOT_OK, operation);
    }
    return s;
  }

  @Override
  public IStatus undo(IUndoContext context, IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
    IUndoableOperation op = getUndoOperation(context);
    return op == null ? Status.CANCEL_STATUS : undoOperation(op, monitor, info);
  }

  @Override
  public IStatus undoOperation(IUndoableOperation operation, IProgressMonitor monitor, IAdaptable info)
      throws ExecutionException {
    if (!operation.canUndo())
      return Status.CANCEL_STATUS;
    fire(OperationHistoryEvent.ABOUT_TO_UNDO, operation);
    IStatus s = operation.undo(monitor == null ? new NullProgressMonitor() : monitor, info);
    if (s.isOK()) {
      synchronized (this) {
        undoList.remove(operation);
        redoList.addLast(operation);
        trim();
      }
      fire(OperationHistoryEvent.UNDONE, operation);
    } else {
      fire(OperationHistoryEvent.OPERATION_NOT_OK, operation);
    }
    return s;
  }
}
