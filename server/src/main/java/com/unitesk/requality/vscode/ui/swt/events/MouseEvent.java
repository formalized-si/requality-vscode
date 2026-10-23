package com.unitesk.requality.vscode.ui.swt.events;

import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.swt.events.MouseEvent}. */
public class MouseEvent extends TypedEvent {
  public int button;
  public int stateMask;
  public int x;
  public int y;
  public int count;

  public MouseEvent(Event e) {
    super(e);
    this.button = e.button;
    this.stateMask = e.stateMask;
    this.x = e.x;
    this.y = e.y;
    this.count = e.count;
  }
}
