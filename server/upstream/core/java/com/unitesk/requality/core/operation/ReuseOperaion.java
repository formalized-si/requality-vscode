package com.unitesk.requality.core.operation;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.nodeiterator.ReuseRequalityChildIterator;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class ReuseOperaion {

  int pos = 0;
  private TreeNode node, target, lastNode;
  public ReuseOperaion(TreeNode node, TreeNode newPar, TreeNode targetNode, boolean addAfter){
    this.node = node;
    target = newPar;
    lastNode = targetNode;
    pos = addAfter?1:-1;
  }
  
  public boolean execute(){
    VirtualNode vn =
        (VirtualNode)
            target.createChild(
                RequalityCLI.getNextId(target, VirtualNode.TYPE_NAME, null),
                VirtualNode.TYPE_NAME);
    try {
      vn.setTarget(node);
      vn.setChildIterator(new ReuseRequalityChildIterator(null, vn.getUUId(), "0"));
      vn.collapse();
      vn.commitAttributes(); //TODO dont updated
      TreeNode child = vn.getWorkChild(0);
      if (!target.getTreeDB().getTreeLogic().canBeChildOf(child, target, false)){
        return false;
      }
      MoveOperationWithAutorename mo = new MoveOperationWithAutorename(vn.getWorkParent().getWorkChildren(),
          vn, target, lastNode, pos>=0);
      mo.excecute();
      if(mo.newId!=null){
        vn.setId(mo.newId);
        vn.commitAttributes();
      }
      child.setIndex(vn.getIndex(),false);
      child.commitAttributes();
      //child.setName(node.getNameOrId());
      //child.saveAttributes();
//      if (target != lastNode) {
//        child.setIndex(
//            Index.getNextIndex(
//                vn.getWorkChild(0).getType(),
//                target,
//                lastNode,
//                vn.getWorkChild(0).getType().equals("TestStep")));
//        vn.setIndex(child.getIndex());
//        child.commitAttributes();
//        vn.commitAttributes();
//      } else if (target.getTreeDB().getTreeLogic().usesIndexByDefault(child.getType())
//          || target.getTreeDB().getSortingSettings().getMethod() == SortingMethod.BY_INDEX) {
//        vn.getWorkChild(0)
//            .setIndex(
//                Index.getNextIndex(
//                    child.getType(), target, lastNode, child.getType().equals("TestStep")));
//        vn.setIndex(child.getIndex());
//        vn.getWorkChild(0).commitAttributes();
//        vn.commitAttributes();
//      }
//      if (pos>=-1 && target.sizeWorkChildren() > 0) {
//        lastNode = target.getSortedWorkChildren().get(target.sizeWorkChildren() - 1);
//      } else {
//        lastNode = child;
//      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return true;
  }
}
