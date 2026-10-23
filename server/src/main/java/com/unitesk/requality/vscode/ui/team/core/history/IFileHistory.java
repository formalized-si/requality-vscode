package com.unitesk.requality.vscode.ui.team.core.history;

/** {@code org.eclipse.team.core.history.IFileHistory}: the revisions of a file and their predecessors and successors. */
public interface IFileHistory {
  /** The complete set of file revisions of the file (predecessors and descendants), or an empty array. */
  IFileRevision[] getFileRevisions();

  /** The revision that corresponds to the content identifier, or null. */
  IFileRevision getFileRevision(String id);

  /** The direct predecessors of the revision, or an empty array. */
  IFileRevision[] getContributors(IFileRevision revision);

  /** The direct descendants of the revision, or an empty array. */
  IFileRevision[] getTargets(IFileRevision revision);
}
