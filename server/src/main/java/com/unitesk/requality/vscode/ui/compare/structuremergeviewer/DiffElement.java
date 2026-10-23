package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.compare.structuremergeviewer.DiffElement}: a diff element with a kind and a parent. */
public abstract class DiffElement implements IDiffElement {
  private int fKind;
  private IDiffContainer fParent;

  public DiffElement(IDiffContainer parent, int kind) {
    fParent = parent;
    fKind = kind;
    if (parent != null)
      parent.add(this);
  }

  @Override
  public Image getImage() {
    return null;
  }

  @Override
  public String getType() {
    return ITypedElement.UNKNOWN_TYPE;
  }

  public void setKind(int kind) {
    fKind = kind;
  }

  @Override
  public int getKind() {
    return fKind;
  }

  @Override
  public IDiffContainer getParent() {
    return fParent;
  }

  @Override
  public void setParent(IDiffContainer parent) {
    fParent = parent;
  }
}
