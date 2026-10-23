package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.Arrays;

/** {@code org.eclipse.jface.viewers.TreePath}. */
public final class TreePath {
  public static final TreePath EMPTY = new TreePath(new Object[0]);
  private final Object[] segments;

  public TreePath(Object[] segments) {
    this.segments = segments.clone();
  }

  public Object getFirstSegment() {
    return segments.length == 0 ? null : segments[0];
  }

  public Object getLastSegment() {
    return segments.length == 0 ? null : segments[segments.length - 1];
  }

  public Object getSegment(int i) {
    return segments[i];
  }

  public int getSegmentCount() {
    return segments.length;
  }

  public TreePath getParentPath() {
    return segments.length == 0 ? EMPTY : new TreePath(Arrays.copyOf(segments, segments.length - 1));
  }

  public TreePath createChildPath(Object child) {
    Object[] s = Arrays.copyOf(segments, segments.length + 1);
    s[segments.length] = child;
    return new TreePath(s);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof TreePath && Arrays.equals(((TreePath) o).segments, segments);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(segments);
  }
}
