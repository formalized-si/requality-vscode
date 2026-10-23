package com.unitesk.requality.core.exception;

import com.unitesk.requality.core.model.TreeNode;

public class CannotBeDeleted extends RuntimeException {
  /**
   * 
   */
  private static final long serialVersionUID = -3979528035877763992L;

  public CannotBeDeleted(TreeNode node){
    super("Node "+node.getUserVisibleName()+" cannot be deleted!");
  }
}
