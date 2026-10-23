package com.unitesk.requality.core.operation;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeNode;

public class CopyPasteHelper {
  private static CopyPasteHelper instance = null;
  private List<NodeDesc> nodesToCopy = null;

  private CopyPasteHelper() {}

  public static CopyPasteHelper getInstance() {
    if (instance == null) instance = new CopyPasteHelper();
    return instance;
  }

  public List<NodeDesc> getNodes() {
    return nodesToCopy;
  }

  public void setNodesDescs(List<NodeDesc> nodes) {
    nodesToCopy = nodes;
  }

  public void setNodes(List<TreeNode> nodes) {
    List<NodeDesc> descs = new ArrayList<>();
    for (TreeNode node : nodes) descs.add(NodeDesc.fromNode(node, true));
    nodesToCopy = descs;
  }

  public boolean isEmpty() {
    if (nodesToCopy == null || nodesToCopy.isEmpty()) return true;
    return false;
  }

  public void setEmpty() {
    nodesToCopy = null;
  }
}
