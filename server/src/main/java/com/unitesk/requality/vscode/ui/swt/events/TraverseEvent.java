package com.unitesk.requality.vscode.ui.swt.events;

import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.swt.events.TraverseEvent}. */
public class TraverseEvent extends KeyEvent {
  public int detail;

  public TraverseEvent(Event e) {
    super(e);
    this.detail = e.detail;
  }
}
