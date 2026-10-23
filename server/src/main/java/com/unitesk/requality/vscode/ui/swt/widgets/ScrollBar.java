package com.unitesk.requality.vscode.ui.swt.widgets;

import com.unitesk.requality.vscode.ui.swt.graphics.Point;

/** {@code org.eclipse.swt.widgets.ScrollBar}: scrolling is done by the browser. */
public class ScrollBar {
  public void setVisible(boolean b) {
  }

  public boolean getVisible() {
    return true;
  }

  public void setIncrement(int i) {
  }

  public void setPageIncrement(int i) {
  }

  public int getSelection() {
    return 0;
  }

  /**
   * {@code org.eclipse.swt.widgets.ScrollBar.getMinimum()}: the renderer does not report the range of a scroll bar, so
   * the SWT default range (minimum 0, maximum 100, thumb 10) is returned.
   */
  public int getMinimum() {
    return 0;
  }

  /** {@code org.eclipse.swt.widgets.ScrollBar.getMaximum()}: SWT default 100 (the renderer does not report it). */
  public int getMaximum() {
    return 100;
  }

  /** {@code org.eclipse.swt.widgets.ScrollBar.getThumb()}: SWT default 10 (the renderer does not report it). */
  public int getThumb() {
    return 10;
  }

  /**
   * {@code org.eclipse.swt.widgets.ScrollBar.getSize()}: the browser draws the scroll bars; the width of the native
   * scroll bar of the platform is returned for the vertical bar (its height is 0).
   */
  public Point getSize() {
    return new Point(NATIVE_WIDTH, 0);
  }

  private static final int NATIVE_WIDTH = 17;

  public void setSelection(int i) {
  }
}
