package com.unitesk.requality.core.model;

public class StructuralChecks {
  public static boolean isDescendant(TreeNode node, TreeNode parent) {  
    return (node.getQualifiedId()+"/").startsWith(parent.getQualifiedId()+"/");
  }
}
