package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.List;

/** {@code org.eclipse.jface.viewers.TreeSelection}. */
public class TreeSelection extends StructuredSelection implements ITreeSelection {
  public static final TreeSelection EMPTY = new TreeSelection(new TreePath[0]);
  private final TreePath[] paths;

  public TreeSelection(TreePath[] paths) {
    super(lastSegments(paths));
    this.paths = paths.clone();
  }

  public TreeSelection(TreePath path) {
    this(path == null ? new TreePath[0] : new TreePath[] { path });
  }

  private static List<Object> lastSegments(TreePath[] paths) {
    List<Object> r = new ArrayList<>();
    for (TreePath p : paths)
      r.add(p.getLastSegment());
    return r;
  }

  @Override
  public TreePath[] getPaths() {
    return paths.clone();
  }

  @Override
  public TreePath[] getPathsFor(Object element) {
    List<TreePath> r = new ArrayList<>();
    for (TreePath p : paths)
      if (element == null ? p.getLastSegment() == null : element.equals(p.getLastSegment()))
        r.add(p);
    return r.toArray(new TreePath[0]);
  }
}
