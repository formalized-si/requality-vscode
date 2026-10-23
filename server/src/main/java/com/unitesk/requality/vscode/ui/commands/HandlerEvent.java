package com.unitesk.requality.vscode.ui.commands;

/** {@code org.eclipse.core.commands.HandlerEvent}. */
public class HandlerEvent {
  private final IHandler handler;
  private final boolean enabledChanged;
  private final boolean handledChanged;

  public HandlerEvent(IHandler handler, boolean enabledChanged, boolean handledChanged) {
    this.handler = handler;
    this.enabledChanged = enabledChanged;
    this.handledChanged = handledChanged;
  }

  public IHandler getHandler() {
    return handler;
  }

  public boolean isEnabledChanged() {
    return enabledChanged;
  }

  public boolean isHandledChanged() {
    return handledChanged;
  }
}
