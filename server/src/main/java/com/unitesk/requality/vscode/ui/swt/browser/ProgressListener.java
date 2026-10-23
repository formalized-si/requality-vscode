package com.unitesk.requality.vscode.ui.swt.browser;

import java.util.function.Consumer;

/** {@code org.eclipse.swt.browser.ProgressListener}. */
public interface ProgressListener {
  void changed(ProgressEvent event);

  void completed(ProgressEvent event);

  static ProgressListener completedAdapter(Consumer<ProgressEvent> c) {
    return new ProgressAdapter() {
      @Override
      public void completed(ProgressEvent e) {
        c.accept(e);
      }
    };
  }

  static ProgressListener changedAdapter(Consumer<ProgressEvent> c) {
    return new ProgressAdapter() {
      @Override
      public void changed(ProgressEvent e) {
        c.accept(e);
      }
    };
  }
}
