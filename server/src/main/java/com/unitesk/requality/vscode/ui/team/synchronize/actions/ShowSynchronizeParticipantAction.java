package com.unitesk.requality.vscode.ui.team.synchronize.actions;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeView;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.ISynchronizeParticipant;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.actions.ShowSynchronizeParticipantAction}: one item of the page drop-down,
 * a radio item that shows its participant in the Synchronize view (checked for the current participant).
 */
public class ShowSynchronizeParticipantAction extends Action {
  private final SynchronizeView view;
  private final ISynchronizeParticipant participant;

  public ShowSynchronizeParticipantAction(SynchronizeView view, ISynchronizeParticipant participant) {
    super(participant.getName(), IAction.AS_RADIO_BUTTON);
    this.view = view;
    this.participant = participant;
    setToolTipText(participant.getName());
    setChecked(view.getParticipant() == participant);
  }

  @Override
  public void run() {
    view.display(participant);
  }
}
