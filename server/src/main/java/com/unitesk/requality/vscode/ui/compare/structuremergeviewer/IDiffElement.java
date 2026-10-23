package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

import com.unitesk.requality.vscode.ui.compare.ITypedElement;

/** {@code org.eclipse.compare.structuremergeviewer.IDiffElement}: a node of a diff tree with its kind of difference. */
public interface IDiffElement extends ITypedElement {
  int getKind();

  IDiffContainer getParent();

  void setParent(IDiffContainer parent);
}
