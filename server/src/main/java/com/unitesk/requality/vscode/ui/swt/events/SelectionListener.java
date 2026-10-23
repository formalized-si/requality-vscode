package com.unitesk.requality.vscode.ui.swt.events;

import java.util.function.Consumer;

/** {@code org.eclipse.swt.events.SelectionListener}. */
public interface SelectionListener {
  void widgetSelected(SelectionEvent e);

  default void widgetDefaultSelected(SelectionEvent e) {
  }

  static SelectionListener widgetSelectedAdapter(Consumer<SelectionEvent> c) {
    return new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        c.accept(e);
      }
    };
  }

  static SelectionListener widgetDefaultSelectedAdapter(Consumer<SelectionEvent> c) {
    return new SelectionAdapter() {
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        c.accept(e);
      }
    };
  }
}
