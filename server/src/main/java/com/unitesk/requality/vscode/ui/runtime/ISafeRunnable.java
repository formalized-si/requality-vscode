package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.ISafeRunnable}: code run by {@link SafeRunner}, handling its own failures. */
public interface ISafeRunnable {
  /** The handler of an exception thrown by {@link #run()}. */
  void handleException(Throwable exception);

  /** Runs the code. */
  void run() throws Exception;
}
