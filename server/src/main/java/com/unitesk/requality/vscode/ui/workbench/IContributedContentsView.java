package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.part.IContributedContentsView}: a view that shows the contents of another part. */
public interface IContributedContentsView {
  /** {@code org.eclipse.ui.part.IContributedContentsView#getContributingPart()}. */
  IWorkbenchPart getContributingPart();
}
