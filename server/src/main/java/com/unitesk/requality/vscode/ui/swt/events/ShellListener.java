package com.unitesk.requality.vscode.ui.swt.events;

/** {@code org.eclipse.swt.events.ShellListener}. Only closing is remoted. */
public interface ShellListener {
  void shellClosed(ShellEvent e);

  default void shellActivated(ShellEvent e) {
  }

  default void shellDeactivated(ShellEvent e) {
  }

  default void shellIconified(ShellEvent e) {
  }

  default void shellDeiconified(ShellEvent e) {
  }
}
