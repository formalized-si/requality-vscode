package com.unitesk.requality.vscode.ui.swt.events;

import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.swt.events.MenuDetectEvent}. */
public class MenuDetectEvent extends TypedEvent {
  public int x;
  public int y;
  public boolean doit;

  public MenuDetectEvent(Event e) {
    super(e);
    this.x = e.x;
    this.y = e.y;
    this.doit = e.doit;
  }
}
