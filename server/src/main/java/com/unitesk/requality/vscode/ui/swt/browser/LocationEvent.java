package com.unitesk.requality.vscode.ui.swt.browser;

import com.unitesk.requality.vscode.ui.swt.events.TypedEvent;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;

/** {@code org.eclipse.swt.browser.LocationEvent}. */
public class LocationEvent extends TypedEvent {
  public String location;
  public boolean top = true;
  public boolean doit = true;

  public LocationEvent(Widget w) {
    super(w);
  }
}
