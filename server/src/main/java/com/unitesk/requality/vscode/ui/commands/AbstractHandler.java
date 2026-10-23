package com.unitesk.requality.vscode.ui.commands;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** {@code org.eclipse.core.commands.AbstractHandler}. */
public abstract class AbstractHandler implements IHandler2 {
  private final List<IHandlerListener> listeners = new CopyOnWriteArrayList<>();
  private boolean baseEnabled = true;

  @Override
  public void addHandlerListener(IHandlerListener handlerListener) {
    listeners.add(handlerListener);
  }

  @Override
  public void removeHandlerListener(IHandlerListener handlerListener) {
    listeners.remove(handlerListener);
  }

  protected void fireHandlerChanged(HandlerEvent handlerEvent) {
    for (IHandlerListener l : listeners)
      l.handlerChanged(handlerEvent);
  }

  protected boolean hasListeners() {
    return !listeners.isEmpty();
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean isEnabled() {
    return baseEnabled;
  }

  protected void setBaseEnabled(boolean state) {
    if (baseEnabled == state)
      return;
    baseEnabled = state;
    fireHandlerChanged(new HandlerEvent(this, true, false));
  }

  @Override
  public void setEnabled(Object evaluationContext) {
  }

  @Override
  public boolean isHandled() {
    return true;
  }
}
