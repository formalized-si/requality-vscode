package com.unitesk.requality.vscode.ui.team.synchronize.patch;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.patch.PatchWizard;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeManager;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeView;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.ISynchronizeParticipant;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.patch.ApplyPatchSynchronizationWizard}: Synchronize with Patch, the
 * synchronize type that shows a patch in the Synchronize view. Its pages are the pages of the Apply Patch wizard; Finish
 * creates the synchronize participant of the patch and shows it in the Synchronize view, the patch is not applied.
 */
public class ApplyPatchSynchronizationWizard extends PatchWizard {
  public ApplyPatchSynchronizationWizard() {
    super(null, null, new CompareConfiguration());
    setNeedsProgressMonitor(true);
    setWindowTitle("Synchronize with Patch");
  }

  /** Finish adds the participant of the patch to the Synchronize view, instead of applying the patch. */
  @Override
  protected void finishOperation() {
    IStorage patch = getPatch();
    ApplyPatchModelSynchronizeParticipant participant = new ApplyPatchModelSynchronizeParticipant(patch, getPatcher(),
        getCompareConfiguration());
    SynchronizeManager manager = SynchronizeManager.getInstance();
    manager.addSynchronizeParticipants(new ISynchronizeParticipant[] { participant });
    SynchronizeView view = manager.showSynchronizeViewInActivePage();
    if (view != null)
      view.display(participant);
  }
}
