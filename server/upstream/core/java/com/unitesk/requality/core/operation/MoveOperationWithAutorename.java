package com.unitesk.requality.core.operation;

import java.util.List;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class MoveOperationWithAutorename extends MoveOperation {
  public String newId = null;
  public List<TreeNode> nodesToBeMoved;
  public MoveOperationWithAutorename(
      List<TreeNode> toBeMoved, 
      TreeNode node, TreeNode newPar, TreeNode targetNode, boolean addAfter) {
    super(node,newPar,targetNode,addAfter);
    nodesToBeMoved = toBeMoved;
  }
  
  @Override
  protected void nodeWithIdExists(TreeNode node, TreeNode conflict, TreeNode newPar, String id) {
    //super.nodeWithIdExists(na,newPar,id);
    String tp = "";
    if (node.getType().equals(Comment.TYPE_NAME))
      tp = Comment.TYPE_NAME + " ";
    newId = RequalityCLI.getNextId(null, newPar, node.getType(), tp);
    node
      .setId(
          RequalityCLI.getNextId(
              node.getWorkParent().getWorkChildren(),
              newPar,
              node.getType(),
              ""));
    node.commitAttributes();
  }
}