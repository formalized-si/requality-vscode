package com.unitesk.requality.vscode.ui.swt.events;

import java.util.function.Consumer;

/** {@code org.eclipse.swt.events.KeyListener}. */
public interface KeyListener {
  void keyPressed(KeyEvent e);

  void keyReleased(KeyEvent e);

  static KeyListener keyPressedAdapter(Consumer<KeyEvent> c) {
    return new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        c.accept(e);
      }
    };
  }

  static KeyListener keyReleasedAdapter(Consumer<KeyEvent> c) {
    return new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        c.accept(e);
      }
    };
  }
}
