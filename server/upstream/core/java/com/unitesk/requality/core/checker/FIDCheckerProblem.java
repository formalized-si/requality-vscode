package com.unitesk.requality.core.checker;

import com.unitesk.requality.core.model.TreeNode;

public class FIDCheckerProblem extends CheckerTreeProblem{
  public FIDCheckerProblem(TreeNode target, String code, String description, String[] allData, TreeNode[]related) {
    super(target, code, description, allData, related);
  }
}