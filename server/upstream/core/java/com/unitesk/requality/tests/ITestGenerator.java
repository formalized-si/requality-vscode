package com.unitesk.requality.tests;

import com.unitesk.requality.core.model.TreeNode;

/**
 * Interface for test generators. Tests tends to be generated with links to tree nodes
 *
 * @author kildishev
 */
public interface ITestGenerator {
  /**
   * Method to generate single test related to targetNode.
   *
   * @param targetNode node which will be tested by created test
   * @param dp provider of supporting functions for test generators, such as call of wizard
   * @return true if test is successfully created, false otherwise
   */
  public boolean generateTest(TreeNode targetNode, Object dp);

  /**
   * Check - is it possible to generate test on specific tree node.
   *
   * @param target node to check on
   * @return true if test can be generated for given tree node, false otherwise
   */
  public boolean isEnabled(TreeNode target);
}
