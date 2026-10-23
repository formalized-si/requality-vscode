package com.unitesk.requality.vscode.ui.swt.events;

import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.swt.events.ControlEvent}. */
public class ControlEvent extends TypedEvent {
  public int x;
  public int y;
  public int width;
  public int height;
  public Object gc;

  public ControlEvent(Event e) {
    super(e);
    this.x = e.x;
    this.y = e.y;
    this.width = e.width;
    this.height = e.height;
  }
}
