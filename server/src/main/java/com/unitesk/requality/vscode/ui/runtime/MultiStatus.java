package com.unitesk.requality.vscode.ui.runtime;

import java.util.ArrayList;
import java.util.List;

/** {@code org.eclipse.core.runtime.MultiStatus}: the severity is the maximum of the children. */
public class MultiStatus implements IStatus {
  private final String plugin;
  private final int code;
  private final String message;
  private final Throwable exception;
  private final List<IStatus> children = new ArrayList<>();
  private int severity = OK;

  public MultiStatus(String pluginId, int code, String message, Throwable exception) {
    this.plugin = pluginId;
    this.code = code;
    this.message = message == null ? "" : message;
    this.exception = exception;
  }

  public MultiStatus(String pluginId, int code, IStatus[] newChildren, String message, Throwable exception) {
    this(pluginId, code, message, exception);
    for (IStatus s : newChildren)
      add(s);
  }

  public void add(IStatus status) {
    children.add(status);
    severity = Math.max(severity, status.getSeverity());
  }

  public void addAll(IStatus status) {
    for (IStatus s : status.getChildren())
      add(s);
  }

  public void merge(IStatus status) {
    if (status.getChildren().length == 0)
      add(status);
    else
      addAll(status);
  }

  @Override
  public IStatus[] getChildren() {
    return children.toArray(new IStatus[0]);
  }

  public boolean isMultiStatus() {
    return true;
  }

  @Override
  public int getSeverity() {
    return severity;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public String getPlugin() {
    return plugin;
  }

  @Override
  public int getCode() {
    return code;
  }

  @Override
  public Throwable getException() {
    return exception;
  }
}
