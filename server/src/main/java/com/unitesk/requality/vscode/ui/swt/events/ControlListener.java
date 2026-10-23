package com.unitesk.requality.vscode.ui.swt.events;

import java.util.function.Consumer;

/** {@code org.eclipse.swt.events.ControlListener}: the remote widgets report no move or resize events. */
public interface ControlListener {
  void controlMoved(ControlEvent e);

  void controlResized(ControlEvent e);

  static ControlListener controlMovedAdapter(Consumer<ControlEvent> c) {
    return new ControlAdapter() {
      @Override
      public void controlMoved(ControlEvent e) {
        c.accept(e);
      }
    };
  }

  static ControlListener controlResizedAdapter(Consumer<ControlEvent> c) {
    return new ControlAdapter() {
      @Override
      public void controlResized(ControlEvent e) {
        c.accept(e);
      }
    };
  }
}
