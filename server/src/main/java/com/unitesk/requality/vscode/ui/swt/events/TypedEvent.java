package com.unitesk.requality.vscode.ui.swt.events;

import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;

/** {@code org.eclipse.swt.events.TypedEvent}. */
public class TypedEvent {
  public Widget widget;
  public Display display;
  public Object data;
  public int time;

  public TypedEvent(Event e) {
    this.widget = e.widget;
    this.display = e.display;
    this.data = e.data;
    this.time = e.time;
  }

  public TypedEvent(Object source) {
    this.widget = source instanceof Widget ? (Widget) source : null;
  }

  public Object getSource() {
    return widget;
  }
}
