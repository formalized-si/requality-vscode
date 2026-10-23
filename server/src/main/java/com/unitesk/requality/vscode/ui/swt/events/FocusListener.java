package com.unitesk.requality.vscode.ui.swt.events;

import java.util.function.Consumer;

/** {@code org.eclipse.swt.events.FocusListener}. */
public interface FocusListener {
  void focusGained(FocusEvent e);

  void focusLost(FocusEvent e);

  static FocusListener focusGainedAdapter(Consumer<FocusEvent> c) {
    return new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        c.accept(e);
      }
    };
  }

  static FocusListener focusLostAdapter(Consumer<FocusEvent> c) {
    return new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        c.accept(e);
      }
    };
  }
}
