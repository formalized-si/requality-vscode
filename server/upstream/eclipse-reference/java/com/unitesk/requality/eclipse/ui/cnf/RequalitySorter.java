package com.unitesk.requality.eclipse.ui.cnf;

import java.text.Collator;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.TreeNodeComparator;

public class RequalitySorter extends ViewerComparator {

  public RequalitySorter() {}

  public RequalitySorter(Collator collator) {
    super(collator);
  }
  private static boolean isSomeProject(Object o){
    return o instanceof IProject;
  }

  private static String getName(Object o){
    if(o instanceof IProject)
      return ((IProject)o).getName();//TODO replace by coreutils call?
    return o.toString();
  }
  @Override
  public int compare(Viewer viewer, Object e1, Object e2) {
    
    if (isSomeProject(e1) && isSomeProject(e2)) {
      return getName(e1).compareTo(getName(e2));
    }
    if (!(e1 instanceof TreeNode)) {
      return super.compare(viewer, e1, e2);
    }
    if (!(e2 instanceof TreeNode)) {
      return super.compare(viewer, e1, e2);
    }
    TreeNode n1 = (TreeNode) e1;
    TreeNode n2 = (TreeNode) e2;
    int i = TreeNodeComparator.getComparator(n1.getTreeDB()).compare(n1, n2);
    ;
    if (i != 0) return i;
    return super.compare(viewer, e1, e2);
  }
}
