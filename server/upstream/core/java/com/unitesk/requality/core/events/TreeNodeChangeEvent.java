package com.unitesk.requality.core.events;

import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeNode;

public class TreeNodeChangeEvent {
  public enum TreeNodeChangeEventType {
    CREATE,
    CHANGE,
    DELETE,
    MOVE,
    SETFOCUS,
    CACHE_CHANGE,
    CHILDREN_ORDER_CHANGE,
  }

  private TreeNodeChangeEventType type;
  private Object target;
  private Object[] args;
  private byte flag;
  private Moment m;

  public TreeNodeChangeEvent(TreeNodeChangeEventType tp, Moment m, Object targ, byte flag, Object... arg) {
    type = tp;
    target = targ;
    this.flag = flag;
    args = arg;
    this.m = m;
  }

  public Moment getMoment(){
    return m;
  }
  
  public byte getFlag() {
    return flag;
  }
  
  public TreeNodeChangeEventType getType() {
    return type;
  }

  public Object[] getArguments() {
    return args;
  }

  public NodeDesc getNodeDescArgument(int i) {
    if (i < 0 || i > args.length) return null;
    Object o = args[i];
    if (o instanceof NodeDesc) return (NodeDesc) o;
    return null;
  }

  public TreeNode getTreeNodeTarget() {
    TreeNode node = null;
    if (target instanceof TreeNode) node = (TreeNode) target;
    if (node != null
        && node.isVirtual()
        && !node.isDisposed()
        && node.getTreeDB() != null
        && !node.getTreeDB().isVirtual())
      try {
        String uid = node.getVRoot();
        if (uid != null)
          node = Instantiator.getExistVirtualTreeDB(node.getTreeDB(), uid).getNode(node.getUUId());
        else node = node.getTreeDB().getNode(node.getUUId());
        // node =
        // Instantiator.getVirtualTreeDB(node.getTreeDB(),Instantiator.DEFAULT_INSTANCE_NAME).getNode(node.getUUId());
      } catch (Exception e) {
      }
    return node;
  }

  public NodeDesc getNodeDescTarget() {
    if (target instanceof NodeDesc) return (NodeDesc) target;
    return null; // NodeDesc.fromNode((TreeNode)target);
  }

  @Override
  public String toString() {
    return m+" "+type + " " + target.toString()+" "+getUUID() + " " + args.length + " " + args;
  }

  private String getUUID() {
    TreeNode node = getTreeNodeTarget();
    return node!=null?node.getSUUId():"null";
  }
}
