package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.compare.IEditableContent;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/**
 * {@code org.eclipse.compare.structuremergeviewer.DiffNode}: a diff container that is also a compare input (the
 * ancestor, left and right elements of one difference).
 */
public class DiffNode extends DiffContainer implements ICompareInput {
  private ITypedElement fAncestor;
  private ITypedElement fLeft;
  private ITypedElement fRight;
  private boolean fDontExpand;
  private List<ICompareInputChangeListener> fListener;
  private boolean fSwapSides;

  public DiffNode(IDiffContainer parent, int kind, ITypedElement ancestor, ITypedElement left, ITypedElement right) {
    this(parent, kind);
    fAncestor = ancestor;
    fLeft = left;
    fRight = right;
  }

  public DiffNode(ITypedElement left, ITypedElement right) {
    this(null, Differencer.CHANGE, null, left, right);
  }

  public DiffNode(int kind, ITypedElement ancestor, ITypedElement left, ITypedElement right) {
    this(null, kind, ancestor, left, right);
  }

  public DiffNode(int kind) {
    super(null, kind);
  }

  public DiffNode(IDiffContainer parent, int kind) {
    super(parent, kind);
  }

  @Override
  public synchronized void addCompareInputChangeListener(ICompareInputChangeListener listener) {
    if (fListener == null)
      fListener = new ArrayList<>();
    if (!fListener.contains(listener))
      fListener.add(listener);
  }

  @Override
  public synchronized void removeCompareInputChangeListener(ICompareInputChangeListener listener) {
    if (fListener != null) {
      fListener.remove(listener);
      if (fListener.isEmpty())
        fListener = null;
    }
  }

  protected void fireChange() {
    List<ICompareInputChangeListener> listeners;
    synchronized (this) {
      listeners = fListener == null ? null : new ArrayList<>(fListener);
    }
    if (listeners != null)
      for (ICompareInputChangeListener l : listeners)
        l.compareInputChanged(this);
  }

  public boolean dontExpand() {
    return fDontExpand;
  }

  public void setDontExpand(boolean dontExpand) {
    fDontExpand = dontExpand;
  }

  /** The element that identifies the node: the ancestor, else the right, else the left. */
  public ITypedElement getId() {
    if (fAncestor != null)
      return fAncestor;
    if (fRight != null)
      return fRight;
    return fLeft;
  }

  @Override
  public String getName() {
    String right = fRight != null ? fRight.getName() : null;
    String left = fLeft != null ? fLeft.getName() : null;
    if (right == null && left == null) {
      if (fAncestor != null)
        return fAncestor.getName();
      return "<no name>";
    }
    if (right == null)
      return left;
    if (left == null)
      return right;
    if (right.equals(left))
      return right;
    String s1 = fSwapSides ? left : right;
    String s2 = fSwapSides ? right : left;
    return MessageFormat.format("{0} / {1}", s1, s2);
  }

  void swapSides(boolean swap) {
    fSwapSides = swap;
  }

  @Override
  public Image getImage() {
    ITypedElement id = getId();
    return id != null ? id.getImage() : null;
  }

  @Override
  public String getType() {
    ITypedElement id = getId();
    return id != null ? id.getType() : ITypedElement.UNKNOWN_TYPE;
  }

  public void setAncestor(ITypedElement ancestor) {
    fAncestor = ancestor;
  }

  @Override
  public ITypedElement getAncestor() {
    return fAncestor;
  }

  public void setLeft(ITypedElement left) {
    fLeft = left;
  }

  @Override
  public ITypedElement getLeft() {
    return fLeft;
  }

  public void setRight(ITypedElement right) {
    fRight = right;
  }

  @Override
  public ITypedElement getRight() {
    return fRight;
  }

  @Override
  public void copy(boolean leftToRight) {
    IDiffContainer pa = getParent();
    if (pa instanceof ICompareInput) {
      ICompareInput parent = (ICompareInput) pa;
      Object dstParent = leftToRight ? parent.getRight() : parent.getLeft();
      if (dstParent instanceof IEditableContent) {
        ITypedElement dst = leftToRight ? getRight() : getLeft();
        ITypedElement src = leftToRight ? getLeft() : getRight();
        dst = ((IEditableContent) dstParent).replace(dst, src);
        if (leftToRight)
          setRight(dst);
        else
          setLeft(dst);
        fireChange();
      }
    }
  }

  @Override
  public int hashCode() {
    int hashCode = 1;
    for (String s : getPath(this, 0))
      hashCode = 31 * hashCode + (s != null ? s.hashCode() : 0);
    return hashCode;
  }

  @Override
  public boolean equals(Object other) {
    if (other != null && getClass() == other.getClass()) {
      String[] path1 = getPath(this, 0);
      String[] path2 = getPath((DiffNode) other, 0);
      if (path1.length != path2.length)
        return false;
      for (int i = 0; i < path1.length; i++)
        if (!path1[i].equals(path2[i]))
          return false;
      return true;
    }
    return super.equals(other);
  }

  private static String[] getPath(ITypedElement el, int level) {
    String[] path = null;
    if (el instanceof IDiffContainer) {
      IDiffContainer parent = ((IDiffContainer) el).getParent();
      if (parent != null)
        path = getPath(parent, level + 1);
    }
    if (path == null)
      path = new String[level + 1];
    path[path.length - 1 - level] = el.getName();
    return path;
  }
}
