package com.unitesk.requality.core.operation.mass;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.checker.ITreeCheckerProblem;
import com.unitesk.requality.core.model.TreeNode;

public class MassCheckersReset extends AbstractMassOperation {
  
  public MassCheckersReset() {
    super("MassCheckersReset");
    setIgnoreTransaction(true);
  }

  @Override
  public String getName() {
    return "Reset all checkers";
  }

  @Override
  public void performSingleAction(TreeNode node) {
    ReentrantLock rl = node.getTreeCheckersLock();
    try {
      rl.lock();
      node.removeTreeCheckerProblems(ITreeCheckerProblem.class);
      node.getTreeDB().getCheckerContainer().addToCheckQ(node, true);
    }finally {
      rl.unlock();
    }
  }

  @Override
  public boolean execute(TreeNode root) {
    CheckerContainer cc = null;
    try {
      cc = root.getTreeDB().getCheckerContainer();
      cc.stopOldThread();
      return super.execute(root);
    }finally {
      if(cc!=null)
        cc.forceCheckQ(false);
    }
  }

  @Override
  public void applySettings(Map<String, String> args) {
  }
}
