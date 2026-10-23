package com.unitesk.requality.vscode.port.eclipse.views;

import com.unitesk.requality.core.model.ITreeNodeProvider;
import com.unitesk.requality.core.model.TreeNode;

public class PrefixAndTreeNode implements ITreeNodeProvider{
  private Object node; 
  private String prefix;
  private String postfix;
  private boolean ignoreNode;
  public PrefixAndTreeNode(Object node, String prefix, String postfix){
    this(false, node, prefix, postfix);
  }
  public PrefixAndTreeNode(boolean ignoreNode, Object node, String prefix, String postfix){
    if(node instanceof PrefixAndTreeNode)
      throw new RuntimeException("cannot store prefix node in prefix node");;
    this.node = node;
    this.prefix = prefix;
    this.postfix = postfix;
    this.ignoreNode = ignoreNode;
  }
  @Override
  public String toString() {
    if(node instanceof PrefixAndTreeNode)
      return prefix+(ignoreNode?"":((TreeNode)((PrefixAndTreeNode)node).node).getUserVisibleName())+postfix;
    return prefix+(ignoreNode?"":(node instanceof TreeNode?((TreeNode)node).getUserVisibleName():node.toString()))+postfix;
  }
 
  public TreeNode getNode() {
    if(node instanceof TreeNode)
      return (TreeNode) node;
    return null;
  }
  public void adapt(boolean ignoreNode, Object node, String prefix, String postfix) {
    this.node = node;
    this.prefix = prefix!=""?prefix:this.prefix;
    this.postfix = postfix!=""?postfix:this.postfix;
    this.ignoreNode = ignoreNode;
  }
  public String getNotNullPrefix() {
    return prefix == null?"":prefix;
  }
}
