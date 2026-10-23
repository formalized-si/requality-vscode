package com.unitesk.requality.vscode.ui.team.synchronize.actions;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.core.ClientApi.Severity;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeManager;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeView;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.ISynchronizeParticipant;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.actions.RemoveSynchronizeParticipantAction}: Remove Current
 * Synchronization and Remove Un-Pinned Synchronizations of the Synchronize view. The removed participants are disposed and
 * the view shows the participant that remains (or the default page).
 * The current synchronization asks for its removal when it is pinned (Remove Pinned Synchronization?), and asks to save the
 * changes of a participant with unsaved changes ({@link ISynchronizeParticipant#isDirty}): Yes saves them, No removes the
 * participant without saving, Cancel keeps it. Remove Un-Pinned Synchronizations asks for the dirty ones only.
 * VSCODE: the dialog lists one participant at a time (Eclipse lists several in one list dialog), and the saveables of the
 * workbench are not ported: the participant saves itself ({@code ApplyPatchModelSynchronizeParticipant.save}).
 */
public class RemoveSynchronizeParticipantAction extends Action {
  private final SynchronizeView view;
  private final boolean removeAll;

  public RemoveSynchronizeParticipantAction(SynchronizeView view, boolean removeAll) {
    this.view = view;
    this.removeAll = removeAll;
    if (removeAll) {
      setText("Remove &Un-Pinned Synchronizations");
      setDescription("Remove Un-Pinned Synchronizations");
      setToolTipText("Remove Un-Pinned Synchronizations");
    } else {
      setText("&Remove Current Synchronization");
      setDescription("Remove Current Synchronization");
      setToolTipText("Remove Current Synchronization");
    }
    setEnabled(false);
  }

  /** The view this action removes the participants from. */
  SynchronizeView getView() {
    return view;
  }

  /** Whether this action removes all the un-pinned participants (Remove Un-Pinned Synchronizations). */
  boolean isRemoveAll() {
    return removeAll;
  }

  @Override
  public void run() {
    List<ISynchronizeParticipant> toRemove = new ArrayList<>();
    if (removeAll) {
      for (ISynchronizeParticipant p : SynchronizeManager.getInstance().getSynchronizeParticipants())
        if (!p.isPinned())
          toRemove.add(p);
      for (ISynchronizeParticipant p : toRemove)
        if (p.isDirty() && !promptToSave(p))
          return;
    } else if (view.getParticipant() != null) {
      ISynchronizeParticipant p = view.getParticipant();
      if (p.isDirty() ? !promptToSave(p) : p.isPinned() && !confirmPinned())
        return;
      toRemove.add(p);
    }
    if (!toRemove.isEmpty())
      SynchronizeManager.getInstance().removeSynchronizeParticipants(toRemove.toArray(new ISynchronizeParticipant[0]));
    view.updateActionEnablements();
  }

  /** Remove Pinned Synchronization?: the pinned synchronization is removed when the user confirms it. */
  private boolean confirmPinned() {
    return Display.getDefault().syncCall(() -> MessageDialog.openQuestion(Display.getDefault().getActiveShell(),
        "Remove Pinned Synchronization?", "The current synchronization is pinned. Are you sure you want to remove it?"));
  }

  /**
   * The unsaved changes of the participant: Yes saves them and continues, No continues without saving, Cancel stops the
   * removal (returns false).
   */
  private boolean promptToSave(ISynchronizeParticipant p) {
    String message = p.getName() + " contains unsaved changes. Would you like to save them?";
    Severity severity = Severity.info;
    int choice = Display.getDefault().syncCall(() -> {
      String r = ServerContext.client().showMessage(severity, "Save Changes", message, null, true, true,
          "Yes", "No", "Cancel");
      return r == null ? 2 : "Yes".equals(r) ? 0 : "No".equals(r) ? 1 : 2;
    });
    if (choice == 0) {
      p.save();
      return true;
    }
    return choice == 1;
  }
}
