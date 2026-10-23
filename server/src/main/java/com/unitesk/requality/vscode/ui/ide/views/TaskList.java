package com.unitesk.requality.vscode.ui.ide.views;

import com.unitesk.requality.vscode.ui.resources.IMarker;

/**
 * {@code org.eclipse.ui.views.TaskList}: the tasks of the workspace (Completed, Priority, Description, Resource, Path,
 * Location, Type), shown in the Requality panel.
 */
public class TaskList extends MarkerView {
  public static final String ID = "org.eclipse.ui.views.TaskList";

  public TaskList() {
    super(IMarker.TASK);
  }

  @Override
  protected String getViewId() {
    return ID;
  }

  @Override
  protected String getDefaultTitle() {
    return "Tasks";
  }
}
