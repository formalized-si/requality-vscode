package com.unitesk.requality.core.events;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

//interface of changing of some node
public interface INodeChangeListener {
  void attributeChange(TreeNode source, String what, Attribute old);

  void attributesChange(TreeNode source, ChangesSet<Attribute> what);

  void deleted(TreeNode source);

  void moved(TreeNode node, TreeNode oldParent);

  void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m);
}
