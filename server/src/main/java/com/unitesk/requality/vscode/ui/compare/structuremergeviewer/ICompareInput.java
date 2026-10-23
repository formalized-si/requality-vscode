package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.compare.structuremergeviewer.ICompareInput}: the ancestor, left and right sides of a comparison. */
public interface ICompareInput {
  String getName();

  Image getImage();

  int getKind();

  ITypedElement getAncestor();

  ITypedElement getLeft();

  ITypedElement getRight();

  void addCompareInputChangeListener(ICompareInputChangeListener listener);

  void removeCompareInputChangeListener(ICompareInputChangeListener listener);

  void copy(boolean leftToRight);
}
