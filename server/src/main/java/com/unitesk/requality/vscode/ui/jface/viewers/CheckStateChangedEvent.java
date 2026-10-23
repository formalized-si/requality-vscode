package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.CheckStateChangedEvent}. */
public class CheckStateChangedEvent {
  private final Object source;
  private final Object element;
  private final boolean state;

  public CheckStateChangedEvent(Object source, Object element, boolean state) {
    this.source = source;
    this.element = element;
    this.state = state;
  }

  public Object getElement() {
    return element;
  }

  public boolean getChecked() {
    return state;
  }

  public Object getSource() {
    return source;
  }

  public ICheckable getCheckable() {
    return (ICheckable) source;
  }
}
