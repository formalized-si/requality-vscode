package com.unitesk.requality.vscode.ui.swt.events;

import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;

/** {@code org.eclipse.swt.events.SelectionEvent}. */
public class SelectionEvent extends TypedEvent {
  public Widget item;
  public int detail;
  public int x;
  public int y;
  public int width;
  public int height;
  public int stateMask;
  public String text;
  public boolean doit;

  public SelectionEvent(Event e) {
    super(e);
    this.item = e.item;
    this.detail = e.detail;
    this.x = e.x;
    this.y = e.y;
    this.width = e.width;
    this.height = e.height;
    this.stateMask = e.stateMask;
    this.text = e.text;
    this.doit = e.doit;
  }
}
