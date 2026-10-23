package com.unitesk.requality.eclipse.wizards;

import org.eclipse.jface.viewers.ISelection;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.VirtualNode;

public class ReportTreenodeSelectionPage extends TreeNodeSelectionPage {

  public ReportTreenodeSelectionPage(ISelection selection, String root, int params, String string) {
    super(selection, root, params, string, TreeNodeIDMode.ProjectSettings);
  }

  @Override
  public boolean checkCanSelectNode() {
    TreeNode[] nodes = getSelectedNodes();
    if (nodes == null) {
      return false;
    }
    for (TreeNode node : nodes)
      if (VirtualNode.TYPE_NAME.equals(node.getType())) {
        updateStatus("Virtual nodes cannot be selected");
        return false;
      }
    return super.checkCanSelectNode();
  }
}
