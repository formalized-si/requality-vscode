package com.unitesk.requality.vscode.ui.commands;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** {@code org.eclipse.core.commands.ExecutionEvent}. */
public final class ExecutionEvent {
  private final Command command;
  private final Map<String, String> parameters;
  private final Object trigger;
  private final Object applicationContext;

  public ExecutionEvent() {
    this(null, Collections.emptyMap(), null, null);
  }

  /** {@code org.eclipse.core.commands.ExecutionEvent(Map, Object, Object)}: an event without a command. */
  @SuppressWarnings("rawtypes")
  public ExecutionEvent(Map parameters, Object trigger, Object applicationContext) {
    this(null, parameters, trigger, applicationContext);
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  public ExecutionEvent(Command command, Map parameters, Object trigger, Object applicationContext) {
    this.command = command;
    this.parameters = parameters == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(parameters));
    this.trigger = trigger;
    this.applicationContext = applicationContext;
  }

  public Command getCommand() {
    return command;
  }

  public Object getApplicationContext() {
    return applicationContext;
  }

  public String getParameter(String parameterId) {
    return parameters.get(parameterId);
  }

  @SuppressWarnings("rawtypes")
  public Map getParameters() {
    return parameters;
  }

  public Object getObjectParameterForExecution(String parameterId) throws ExecutionException {
    return parameters.get(parameterId);
  }

  public Object getTrigger() {
    return trigger;
  }

  @Override
  public String toString() {
    return "ExecutionEvent(" + command + "," + parameters + ")";
  }
}
