package com.unitesk.requality.vscode.ui.jface.operation;

import java.lang.reflect.InvocationTargetException;

/** {@code org.eclipse.jface.operation.IRunnableContext}. */
public interface IRunnableContext {
  void run(boolean fork, boolean cancelable, IRunnableWithProgress runnable)
      throws InvocationTargetException, InterruptedException;
}
