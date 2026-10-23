package com.unitesk.requality.vscode.ui.swt.browser;

import java.util.function.Consumer;

/** {@code org.eclipse.swt.browser.LocationListener}. */
public interface LocationListener {
  void changing(LocationEvent event);

  void changed(LocationEvent event);

  static LocationListener changingAdapter(Consumer<LocationEvent> c) {
    return new LocationAdapter() {
      @Override
      public void changing(LocationEvent e) {
        c.accept(e);
      }
    };
  }

  static LocationListener changedAdapter(Consumer<LocationEvent> c) {
    return new LocationAdapter() {
      @Override
      public void changed(LocationEvent e) {
        c.accept(e);
      }
    };
  }
}
