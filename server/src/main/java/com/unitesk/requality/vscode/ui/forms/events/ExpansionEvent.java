package com.unitesk.requality.vscode.ui.forms.events;

/** {@code org.eclipse.ui.forms.events.ExpansionEvent}: the source and the new expansion state. */
public class ExpansionEvent {
  private final Object source;
  private final boolean expanded;

  public ExpansionEvent(Object source, boolean state) {
    this.source = source;
    this.expanded = state;
  }

  public Object getSource() {
    return source;
  }

  public boolean getState() {
    return expanded;
  }
}
