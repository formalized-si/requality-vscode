package com.unitesk.requality.core.operation.mass;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public abstract class AbstractMassOperation extends AbstractNodeOperation {

  // TODO autoregister by name in registry
  public AbstractMassOperation(String comment) {
    super(comment);
  }

  @Override
  public void performAction(TreeNode root) {
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(root, false, false, true);
    for (TreeNode tn : dftw) {
      performSingleAction(tn);
    }
  }

  public abstract void performSingleAction(TreeNode root);
}
