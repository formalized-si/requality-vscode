package com.unitesk.requality.vscode.ui.runtime;

import com.unitesk.requality.core.log.RLogger;

/**
 * {@code org.eclipse.core.runtime.SafeRunner}: runs an {@link ISafeRunnable}; an exception is passed to its
 * {@code handleException} (the failure is logged, as the platform status manager does).
 */
public final class SafeRunner {
  private SafeRunner() {
  }

  /** Runs the runnable; its exceptions go to {@link ISafeRunnable#handleException(Throwable)}. */
  public static void run(ISafeRunnable code) {
    try {
      code.run();
    } catch (Exception e) {
      RLogger.logError(e);
      code.handleException(e);
    }
  }
}
