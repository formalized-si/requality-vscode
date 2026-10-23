package com.unitesk.requality.vscode.ui.swt.browser;

import com.unitesk.requality.vscode.ui.swt.events.TypedEvent;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;

/** {@code org.eclipse.swt.browser.ProgressEvent}. */
public class ProgressEvent extends TypedEvent {
  public int current;
  public int total;

  public ProgressEvent(Widget w) {
    super(w);
  }
}
