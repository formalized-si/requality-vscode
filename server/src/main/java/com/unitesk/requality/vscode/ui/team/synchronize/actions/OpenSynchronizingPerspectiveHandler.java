package com.unitesk.requality.vscode.ui.team.synchronize.actions;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.team.synchronize.TeamSynchronizingPerspective;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.WorkbenchException;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.TeamSynchronizingPerspective}, the command
 * {@code org.eclipse.team.ui.TeamSynchronizingPerspective} (Window &gt; Open Perspective &gt; Team Synchronizing): opens
 * the Team Synchronizing perspective in the active window.
 */
public class OpenSynchronizingPerspectiveHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) {
    try {
      PlatformUI.getWorkbench().showPerspective(TeamSynchronizingPerspective.ID, null);
    } catch (WorkbenchException e) {
      throw new IllegalStateException(e);
    }
    return null;
  }
}
