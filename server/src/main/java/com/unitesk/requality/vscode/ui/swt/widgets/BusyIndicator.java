package com.unitesk.requality.vscode.ui.swt.widgets;

/** {@code org.eclipse.swt.custom.BusyIndicator}. */
public final class BusyIndicator {
  private BusyIndicator() {
  }

  public static void showWhile(Display display, Runnable runnable) {
    runnable.run();
  }
}
