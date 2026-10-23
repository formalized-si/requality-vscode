package com.unitesk.requality.vscode.ui.team.ui.synchronize;

/**
 * {@code org.eclipse.team.ui.synchronize.ISynchronizeManager}: the synchronize participants of the workbench, shown in the
 * Synchronize view ({@link com.unitesk.requality.vscode.ui.team.ui.TeamUI#getSynchronizeManager}).
 */
public interface ISynchronizeManager {
  /** A listener of the changes of the participants. */
  interface Listener {
    /** The participants were added or removed. */
    void participantsChanged();
  }

  /** The participants, in the order they were added. */
  ISynchronizeParticipant[] getSynchronizeParticipants();

  /** Adds the participants (the Synchronize view shows them with {@code showSynchronizeViewInActivePage}). */
  void addSynchronizeParticipants(ISynchronizeParticipant[] participants);

  /** Removes the participants and disposes them. */
  void removeSynchronizeParticipants(ISynchronizeParticipant[] participants);

  /** Adds a listener of the changes of the participants. */
  void addListener(Listener listener);

  /** Removes a listener of the changes of the participants. */
  void removeListener(Listener listener);
}
