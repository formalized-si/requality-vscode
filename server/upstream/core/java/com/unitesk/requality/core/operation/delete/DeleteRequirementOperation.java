package com.unitesk.requality.core.operation.delete;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.VirtualNode;

public class DeleteRequirementOperation {
  private static DeleteRequirementOperation one = new DeleteRequirementOperation();
  public static DeleteRequirementOperation getOne() {
    return one;
  }

  private DeleteRequirementOperation() {
  }

  public void process(TreeNode[] nodes, List<UUID> proc) {
    for (TreeNode node : nodes) {
      if (node == null)
        continue;
      if (proc.contains(node.getUUId())) continue;
      if (!(node.getWorkParent() instanceof Requirement)
          && !(node.getWorkParent() instanceof VirtualNode)) continue;
      TreeDB db = node.getTreeDB();
      if (!db.getTreeLogic().canBeDeleted(node)) continue;

      //collectLocations((Requirement)node, collected);
    }
    //        if(!collected.isEmpty()){
    //          deleteLocations(collected, processed);
    //        }
    for (TreeNode node : nodes){
      if(node == null)
        continue;
      if (!proc.contains(node.getUUId())) {
        List<UUID> toprocess = new ArrayList<>();
        recUpdateProcessed(node, toprocess);
        node.delete();
        proc.addAll(toprocess);
      }
    }
  }
  public void recUpdateProcessed(TreeNode node, List<UUID> processed) {
    if (node == null) return;
    processed.add(node.getUUId());
    for (UUID uid : node.getWorkChildrenUUIDs(true)) {
      TreeNode nde = node.getTreeDB().getNode(uid);
      recUpdateProcessed(nde, processed);
    }
  }
}
