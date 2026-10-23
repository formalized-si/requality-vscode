package com.unitesk.requality.vscode.ui.navigator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;

/** {@code org.eclipse.ui.internal.navigator.extensions.ExtensionStateModel}: a property map with change events. */
public class ExtensionStateModel implements IExtensionStateModel {
  private final String id;
  private final Map<String, Object> values = new ConcurrentHashMap<>();
  private final List<IPropertyChangeListener> listeners = new CopyOnWriteArrayList<>();

  public ExtensionStateModel(String id) {
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean getBooleanProperty(String aPropertyName) {
    return Boolean.TRUE.equals(values.get(aPropertyName));
  }

  @Override
  public void setBooleanProperty(String aPropertyName, boolean aPropertyValue) {
    boolean old = getBooleanProperty(aPropertyName);
    values.put(aPropertyName, aPropertyValue);
    if (old != aPropertyValue)
      for (IPropertyChangeListener l : listeners)
        l.propertyChange(new PropertyChangeEvent(this, aPropertyName, old, aPropertyValue));
  }

  @Override
  public void addPropertyChangeListener(IPropertyChangeListener aListener) {
    if (!listeners.contains(aListener))
      listeners.add(aListener);
  }

  @Override
  public void removePropertyChangeListener(IPropertyChangeListener aListener) {
    listeners.remove(aListener);
  }
}
