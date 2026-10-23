package com.unitesk.requality.vscode.ui.jface.preference;

/** {@code org.eclipse.jface.util.PropertyChangeEvent}. */
public class PropertyChangeEvent {
  private final Object source;
  private final String property;
  private final Object oldValue;
  private final Object newValue;

  public PropertyChangeEvent(Object source, String property, Object oldValue, Object newValue) {
    this.source = source;
    this.property = property;
    this.oldValue = oldValue;
    this.newValue = newValue;
  }

  public Object getSource() {
    return source;
  }

  public String getProperty() {
    return property;
  }

  public Object getOldValue() {
    return oldValue;
  }

  public Object getNewValue() {
    return newValue;
  }
}
