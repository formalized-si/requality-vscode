package com.unitesk.requality.vscode.ui.jface.window;

import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/** {@code org.eclipse.jface.window.DefaultToolTip}. */
public class DefaultToolTip extends ToolTip {
  public DefaultToolTip(Control control) {
    super(control);
  }

  public DefaultToolTip(Control control, int style, boolean manualActivation) {
    super(control, style, manualActivation);
  }

  public void setText(String text) {
    if (control != null && !control.isDisposed())
      control.setToolTipText(text);
  }

  public void setImage(Object image) {
  }

  public void setBackgroundColor(Object c) {
  }

  public void setForegroundColor(Object c) {
  }
}
