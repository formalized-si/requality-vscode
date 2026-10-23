package com.unitesk.requality.vscode.ui.team.ui.synchronize;

import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;

/**
 * {@code org.eclipse.team.ui.synchronize.AbstractSynchronizeParticipant}: the name, the description and the pinned state of a
 * synchronize participant, with the property change events of the pinned state ({@link ISynchronizeParticipant#P_PINNED}).
 */
public abstract class AbstractSynchronizeParticipant implements ISynchronizeParticipant {
  private String name;
  private String description = "";
  private boolean pinned;
  private final CopyOnWriteArrayList<IPropertyChangeListener> listeners = new CopyOnWriteArrayList<>();

  protected AbstractSynchronizeParticipant(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  /** Sets the name of the synchronization ({@code AbstractSynchronizeParticipant.setName}). */
  protected void setName(String name) {
    this.name = name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  /** Sets the description of the synchronization, the content description of the view. */
  protected void setDescription(String description) {
    this.description = description == null ? "" : description;
  }

  @Override
  public boolean isPinned() {
    return pinned;
  }

  @Override
  public void setPinned(boolean pinned) {
    boolean old = this.pinned;
    this.pinned = pinned;
    if (old != pinned) {
      PropertyChangeEvent event = new PropertyChangeEvent(this, P_PINNED, old, pinned);
      for (IPropertyChangeListener l : listeners)
        l.propertyChange(event);
    }
  }

  @Override
  public void addPropertyChangeListener(IPropertyChangeListener listener) {
    listeners.addIfAbsent(listener);
  }

  @Override
  public void removePropertyChangeListener(IPropertyChangeListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void dispose() {
    listeners.clear();
  }
}
