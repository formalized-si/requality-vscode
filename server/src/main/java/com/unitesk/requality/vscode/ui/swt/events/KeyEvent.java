package com.unitesk.requality.vscode.ui.swt.events;

import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.swt.events.KeyEvent}. */
public class KeyEvent extends TypedEvent {
  public char character;
  public int keyCode;
  public int keyLocation;
  public int stateMask;
  public boolean doit;

  public KeyEvent(Event e) {
    super(e);
    this.character = e.character;
    this.keyCode = e.keyCode;
    this.keyLocation = e.keyLocation;
    this.stateMask = e.stateMask;
    this.doit = e.doit;
  }
}
