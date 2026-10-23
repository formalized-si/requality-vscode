package com.unitesk.requality.vscode.ui.team.synchronize.actions;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeView;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.ISynchronizeParticipant;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.actions.PinParticipantAction}: Pin Current Synchronization, the pin of the
 * current synchronize participant of the Synchronize view. Checked while the participant is pinned; disabled without one.
 */
public class PinParticipantAction extends Action {
  private final SynchronizeView view;
  private ISynchronizeParticipant participant;
  private final IPropertyChangeListener pinListener = this::pinnedChanged;

  public PinParticipantAction(SynchronizeView view) {
    super("Pi&n Current Synchronization", IAction.AS_CHECK_BOX);
    this.view = view;
    setDescription("Pin Current Synchronization");
    setToolTipText("Pin Current Synchronization");
    setEnabled(false);
  }

  /** Shows the pin state of the participant (null: disabled and unchecked) ({@code PinParticipantAction.setParticipant}). */
  public void setParticipant(ISynchronizeParticipant p) {
    if (participant != null)
      participant.removePropertyChangeListener(pinListener);
    participant = p;
    if (p != null)
      p.addPropertyChangeListener(pinListener);
    update();
  }

  private void pinnedChanged(PropertyChangeEvent event) {
    update();
  }

  private void update() {
    setEnabled(participant != null);
    setChecked(participant != null && participant.isPinned());
  }

  @Override
  public void run() {
    if (participant == null)
      return;
    participant.setPinned(!participant.isPinned());
    update();
  }

  /** The view of the action. */
  SynchronizeView getView() {
    return view;
  }
}
