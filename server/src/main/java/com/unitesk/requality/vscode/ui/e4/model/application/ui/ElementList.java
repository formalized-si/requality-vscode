package com.unitesk.requality.vscode.ui.e4.model.application.ui;

import java.util.ArrayList;
import java.util.Collection;

/**
 * The children of an E4 container: an element added here leaves its previous container and gets this container as
 * its parent (the containment of EMF), so a part moved between editor groups is in one group only.
 */
public final class ElementList<T extends MUIElement> extends ArrayList<T> {
  private static final long serialVersionUID = 1L;
  private final transient MElementContainer<MUIElement> owner;

  public ElementList(MElementContainer<MUIElement> owner) {
    this.owner = owner;
  }

  @Override
  public boolean add(T e) {
    add(size(), e);
    return true;
  }

  @Override
  public void add(int index, T e) {
    MElementContainer<MUIElement> old = e.getParent();
    if (old != null)
      old.getChildren().remove(e);
    super.add(Math.min(index, size()), e);
    e.setParent(owner);
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    for (T e : c)
      add(e);
    return !c.isEmpty();
  }

  @Override
  public boolean remove(Object o) {
    boolean removed = super.remove(o);
    if (removed && o instanceof MUIElement && ((MUIElement) o).getParent() == owner)
      ((MUIElement) o).setParent(null);
    return removed;
  }

  @Override
  public T remove(int index) {
    T removed = super.remove(index);
    if (removed.getParent() == owner)
      removed.setParent(null);
    return removed;
  }

  @Override
  public void clear() {
    for (T e : this)
      if (e.getParent() == owner)
        e.setParent(null);
    super.clear();
  }
}
