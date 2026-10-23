package com.unitesk.requality.vscode.ui.swt.events;

/** {@code org.eclipse.swt.events.PaintListener}: the webview paints the widgets, custom painting is not called. */
@FunctionalInterface
public interface PaintListener {
  void paintControl(PaintEvent e);
}
