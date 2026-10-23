package com.unitesk.requality.vscode.ui.compare;

/**
 * {@code org.eclipse.compare.internal.ReplaceWithPreviousEditionAction}: Replace With &gt; Previous from Local History: the
 * file is replaced with the most recent state that differs from it, without a dialog.
 */
public class ReplaceWithPreviousEditionAction extends EditionAction {
  public ReplaceWithPreviousEditionAction() {
    super(true, "org.eclipse.compare.internal.ReplaceWithEditionAction");
    fPrevious = true;
  }
}
