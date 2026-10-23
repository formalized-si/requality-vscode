package com.unitesk.requality.vscode.ui.team.synchronize;

import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.ISynchronizeManager;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.ISynchronizeParticipant;
import com.unitesk.requality.vscode.ui.workbench.IViewPart;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.SynchronizeManager}: the synchronize participants of the workbench, in the
 * order they were added. The participants are shown one at a time in the Synchronize view, which listens to the changes.
 */
public final class SynchronizeManager implements ISynchronizeManager {
  private static final SynchronizeManager INSTANCE = new SynchronizeManager();

  private final CopyOnWriteArrayList<ISynchronizeParticipant> participants = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

  private SynchronizeManager() {
  }

  /** The synchronize manager of the workbench ({@code TeamUI.getSynchronizeManager}). */
  public static SynchronizeManager getInstance() {
    return INSTANCE;
  }

  @Override
  public ISynchronizeParticipant[] getSynchronizeParticipants() {
    return participants.toArray(new ISynchronizeParticipant[0]);
  }

  @Override
  public void addSynchronizeParticipants(ISynchronizeParticipant[] added) {
    boolean changed = false;
    for (ISynchronizeParticipant p : added) {
      if (!participants.contains(p)) {
        participants.add(p);
        changed = true;
      }
    }
    if (changed)
      fire();
  }

  @Override
  public void removeSynchronizeParticipants(ISynchronizeParticipant[] removed) {
    boolean changed = false;
    for (ISynchronizeParticipant p : removed) {
      if (participants.remove(p)) {
        p.dispose();
        changed = true;
      }
    }
    if (changed)
      fire();
  }

  @Override
  public void addListener(Listener listener) {
    listeners.addIfAbsent(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  /**
   * Shows the Synchronize view in the active page and returns it, so that the caller displays its participant
   * ({@code TeamUI.showSynchronizeViewInActivePage}). Returns null when the view cannot be shown.
   */
  public SynchronizeView showSynchronizeViewInActivePage() {
    try {
      IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
      IViewPart part = page.showView(SynchronizeView.ID);
      return part instanceof SynchronizeView view ? view : null;
    } catch (PartInitException e) {
      RLogger.logError(e);
      return null;
    }
  }

  private void fire() {
    for (Listener l : listeners)
      l.participantsChanged();
  }
}
