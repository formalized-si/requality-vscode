package com.unitesk.requality.vscode.ui.team.ui.history;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.workbench.Page;

/**
 * {@code org.eclipse.team.ui.history.HistoryPage}: the abstract history page that keeps track of its site, its input and
 * its history view. Subclasses implement {@link #inputSet()}, which is called after {@link #setInput(Object)}.
 */
public abstract class HistoryPage extends Page implements IHistoryPage {
  private IHistoryPageSite site;
  private Object input;
  private IHistoryView historyView;
  private final List<IPropertyChangeListener> listeners = new ArrayList<>();

  @Override
  public void setSite(IHistoryPageSite site) {
    this.site = site;
  }

  @Override
  public IHistoryPageSite getHistoryPageSite() {
    return site;
  }

  @Override
  public Object getInput() {
    return input;
  }

  @Override
  public boolean setInput(Object object) {
    this.input = object;
    return inputSet();
  }

  /** Called after {@link #setInput(Object)}; the input is available from {@link #getInput()}. */
  public abstract boolean inputSet();

  public void setHistoryView(IHistoryView historyView) {
    this.historyView = historyView;
  }

  @Override
  public IHistoryView getHistoryView() {
    return historyView;
  }

  @Override
  public void addPropertyChangeListener(IPropertyChangeListener listener) {
    synchronized (listeners) {
      if (!listeners.contains(listener))
        listeners.add(listener);
    }
  }

  @Override
  public void removePropertyChangeListener(IPropertyChangeListener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  /** Notifies the listeners that a property of the page has changed. */
  protected void firePropertyChange(Object source, String property, Object oldValue, Object newValue) {
    List<IPropertyChangeListener> ls;
    synchronized (listeners) {
      ls = new ArrayList<>(listeners);
    }
    PropertyChangeEvent event = new PropertyChangeEvent(source, property, oldValue, newValue);
    for (IPropertyChangeListener l : ls)
      l.propertyChange(event);
  }
}
