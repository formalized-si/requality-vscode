package com.unitesk.requality.vscode.ui.jface.operation;

import java.lang.reflect.InvocationTargetException;

import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.jface.operation.IRunnableWithProgress}. */
@FunctionalInterface
public interface IRunnableWithProgress {
  void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException;
}
