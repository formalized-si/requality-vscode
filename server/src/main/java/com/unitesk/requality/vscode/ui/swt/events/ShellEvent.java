package com.unitesk.requality.vscode.ui.swt.events;

import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.swt.events.ShellEvent}. */
public class ShellEvent extends TypedEvent {
  public boolean doit;

  public ShellEvent(Event e) {
    super(e);
    this.doit = e.doit;
  }
}
