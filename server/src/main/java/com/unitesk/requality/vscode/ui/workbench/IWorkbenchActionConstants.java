package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory;

/**
 * {@code org.eclipse.ui.IWorkbenchActionConstants}: ids of the workbench menu groups and of the global actions that
 * {@link ActionFactory} does not define.
 */
public interface IWorkbenchActionConstants {
  String MB_ADDITIONS = "additions";
  String GO_INTO = ActionFactory.GO_INTO.getId();
  String UP = ActionFactory.UP.getId();
  String GO_TO_RESOURCE = ActionFactory.GO_TO_RESOURCE.getId();
}
