package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

/**
 * {@code org.eclipse.compare.structuremergeviewer.IStructureComparator}: an element of a structure compared by the
 * {@link Differencer} (the children are matched with {@link #equals(Object)}).
 */
public interface IStructureComparator {
  Object[] getChildren();

  @Override
  boolean equals(Object other);
}
