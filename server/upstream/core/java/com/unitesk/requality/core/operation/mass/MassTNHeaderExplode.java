package com.unitesk.requality.core.operation.mass;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.tool.Index;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class MassTNHeaderExplode extends AbstractMassOperation {

  public MassTNHeaderExplode() {
    super("Remove excess tn headers");
  }

  @Override
  public String getName() {
    return "HEADer explode";
  }

  @Override
  public void performSingleAction(TreeNode root) {
    if (root.getType().equals(TextNode.TYPE_NAME)) {
      TextNode tn = (TextNode) root;
      // if(tn.getTextNodeType().equals("header")){
      List<TreeNode> childs = tn.getWorkChildren();
      String ds = tn.getDescription();
      boolean delroot = false;
      if (ds == null || ds.trim().isEmpty()) delroot = true;
      TreeNode tnpar = tn.getWorkParent();
      TreeNode next = tn;
      Set<String> ids = new HashSet<>();
      for (TreeNode nd : tnpar.getWorkChildren()) ids.add(nd.getId());
      for (TreeNode node : childs) ids.add(node.getId());
      for (TreeNode node : childs) {
        node.setIndex(Index.getNextIndex(null, tnpar, next),false);
        node.setId(RequalityCLI.getNextId(ids));
        node.commitAttributes();
        ids.add(node.getId());
        node.setParent(tnpar);
      }
      if (delroot) tn.delete();
      // }
    }
  }

  @Override
  public void applySettings(Map<String, String> args) {
    // TODO Auto-generated method stub

  }
}
