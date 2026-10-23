package com.unitesk.requality.eclipse.views.properties;

import org.eclipse.jface.viewers.IFilter;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.ReportFolder;

public class RootReportFilter implements IFilter {

  @Override
  public boolean select(Object toTest) {
    TreeNode node = (TreeNode) toTest;
    if (node != null) return !node.getWorkParent().getType().equals(ReportFolder.TYPE_NAME);
    return false;
  }
}
