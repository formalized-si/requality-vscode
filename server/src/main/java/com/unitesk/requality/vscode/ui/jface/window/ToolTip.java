package com.unitesk.requality.vscode.ui.jface.window;

import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/** {@code org.eclipse.jface.window.ToolTip}: shown as the native tooltip of the control. */
public abstract class ToolTip {
  public static final int RECREATE = 1;
  public static final int NO_RECREATE = 1 << 1;
  protected final Control control;

  protected ToolTip(Control control) {
    this.control = control;
  }

  protected ToolTip(Control control, int style, boolean manualActivation) {
    this.control = control;
  }

  public void setHideDelay(int ms) {
  }

  public void setPopupDelay(int ms) {
  }

  public void setShift(Object p) {
  }

  public void activate() {
  }

  public void deactivate() {
  }

  public void hide() {
  }
}
