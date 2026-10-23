package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

/** {@code org.eclipse.compare.structuremergeviewer.IDiffContainer}: a diff element with children. */
public interface IDiffContainer extends IDiffElement {
  boolean hasChildren();

  IDiffElement[] getChildren();

  void add(IDiffElement child);

  void removeToRoot(IDiffElement child);
}
