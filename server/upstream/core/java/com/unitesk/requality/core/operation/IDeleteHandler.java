package com.unitesk.requality.core.operation;

import java.util.List;
import java.util.UUID;

import com.unitesk.requality.core.model.TreeNode;

public interface IDeleteHandler {
  public boolean isSuitableFor(TreeNode tn);

  public String[] getWarnings(TreeNode[] nodes);

  public void process(TreeNode[] nodes, List<UUID> deleted, byte flag)
      throws Exception;

  public String titleOverride(TreeNode[] nodes);
}
