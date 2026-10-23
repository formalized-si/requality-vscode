package com.unitesk.requality.vscode.ui.swt.events;

import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.swt.events.VerifyEvent}: the new text replacing [start, end). */
public class VerifyEvent extends KeyEvent {
  public int start;
  public int end;
  public String text;

  public VerifyEvent(Event e) {
    super(e);
    this.start = e.start;
    this.end = e.end;
    this.text = e.text;
  }
}
