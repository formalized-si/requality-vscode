package com.unitesk.requality.core.checker;

import java.util.List;
import com.unitesk.requality.core.model.TreeNode;

/** Class used to detect problems over Tree Nodes */
public interface INodeChecker {
  /**
   * Method used to check single node for problems
   *
   * @param node node to check
   * @param problems
   * @return array of problems, empty if there is no problems
   */
  public abstract boolean check(TreeNode node, List<IMark> problems);
}
