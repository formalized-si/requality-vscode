package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

import java.util.ArrayList;

/** {@code org.eclipse.compare.structuremergeviewer.DiffContainer}: a diff element with a list of children. */
public abstract class DiffContainer extends DiffElement implements IDiffContainer {
  private static final IDiffElement[] fgEmptyArray = new IDiffElement[0];
  private ArrayList<IDiffElement> fChildren;

  public DiffContainer(IDiffContainer parent, int kind) {
    super(parent, kind);
  }

  /** The child with the name, or null. */
  public IDiffElement findChild(String name) {
    for (IDiffElement child : getChildren())
      if (name.equals(child.getName()))
        return child;
    return null;
  }

  @Override
  public void add(IDiffElement diff) {
    if (fChildren == null)
      fChildren = new ArrayList<>();
    fChildren.add(diff);
    diff.setParent(this);
  }

  @Override
  public void removeToRoot(IDiffElement child) {
    if (fChildren != null) {
      fChildren.remove(child);
      child.setParent(null);
      if (fChildren.isEmpty()) {
        IDiffContainer p = getParent();
        if (p != null)
          p.removeToRoot(this);
      }
    }
  }

  public void remove(IDiffElement child) {
    if (fChildren != null) {
      fChildren.remove(child);
      child.setParent(null);
    }
  }

  @Override
  public boolean hasChildren() {
    return fChildren != null && fChildren.size() > 0;
  }

  @Override
  public IDiffElement[] getChildren() {
    if (fChildren != null)
      return fChildren.toArray(fgEmptyArray);
    return fgEmptyArray;
  }
}
