package com.unitesk.requality.vscode.ui.team.synchronize.actions;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeView;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.actions.ToggleLinkingAction}: Lin&amp;k with Editor, the linking of the
 * Synchronize view with the active editor.
 */
public class ToggleLinkingAction extends Action {
  private final SynchronizeView view;

  public ToggleLinkingAction(SynchronizeView view) {
    super("Lin&k with Editor");
    setDescription("Link with active editor");
    setToolTipText("Link with Editor and Selection");
    this.view = view;
    setChecked(view.isLinkingEnabled());
  }

  @Override
  public void run() {
    view.setLinkingEnabled(isChecked());
  }
}
