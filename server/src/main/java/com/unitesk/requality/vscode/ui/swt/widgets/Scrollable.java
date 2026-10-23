package com.unitesk.requality.vscode.ui.swt.widgets;

import com.unitesk.requality.vscode.ui.swt.graphics.Rectangle;

/** {@code org.eclipse.swt.widgets.Scrollable}. */
public abstract class Scrollable extends Control {
  protected Scrollable(Composite parent, int style, String kind) {
    super(parent, style, kind);
  }

  public Rectangle getClientArea() {
    return getBounds();
  }

  public ScrollBar getVerticalBar() {
    return new ScrollBar();
  }

  public ScrollBar getHorizontalBar() {
    return new ScrollBar();
  }
}
