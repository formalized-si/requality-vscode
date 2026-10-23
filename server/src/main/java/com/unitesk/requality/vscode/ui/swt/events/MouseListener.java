package com.unitesk.requality.vscode.ui.swt.events;

import java.util.function.Consumer;

/** {@code org.eclipse.swt.events.MouseListener}. */
public interface MouseListener {
  void mouseDoubleClick(MouseEvent e);

  void mouseDown(MouseEvent e);

  void mouseUp(MouseEvent e);

  static MouseListener mouseDoubleClickAdapter(Consumer<MouseEvent> c) {
    return new MouseAdapter() {
      @Override
      public void mouseDoubleClick(MouseEvent e) {
        c.accept(e);
      }
    };
  }

  static MouseListener mouseDownAdapter(Consumer<MouseEvent> c) {
    return new MouseAdapter() {
      @Override
      public void mouseDown(MouseEvent e) {
        c.accept(e);
      }
    };
  }

  static MouseListener mouseUpAdapter(Consumer<MouseEvent> c) {
    return new MouseAdapter() {
      @Override
      public void mouseUp(MouseEvent e) {
        c.accept(e);
      }
    };
  }
}
