package com.unitesk.requality.core.exception;

import com.unitesk.requality.core.model.TreeNode;

public class NodeWithIdExistException extends RuntimeException {

  /**
   * 
   */
  private static final long serialVersionUID = -6214039527334759040L;
  public TreeNode node;
  public String id;
  public TreeNode par;
  
  public NodeWithIdExistException(TreeNode na, String id, TreeNode par) {
    super("Node with conflicting id '"+na.getId()+"' was already exist on '"+par.getUserVisibleName()+"'" );
    this.node = na;
    this.id = id;
    this.par = par;
  }

}
