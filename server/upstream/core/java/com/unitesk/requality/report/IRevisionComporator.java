package com.unitesk.requality.report;

import java.util.Map;
import java.util.UUID;
import com.unitesk.requality.core.attribute.IReqURIResolver;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;

public interface IRevisionComporator {
  // Tree should begin by Requirement
  public TreeNode getRootReq();
  
  // gathering and adding to the list deleted requirements
  public java.util.List<TreeNode> getGatheredChildren(TreeNode node);
  public String getRevColor(TreeNode node);
  public void compareRevisions(String rev1, String rev2, IReqURIResolver reslv);
  public TreeDB getRevisionTree(TreeDB baseDB, String rev);
  // get certain revision tree nodes
  public Map<UUID, TreeNode> getRevisionNodes(TreeDB tree);
  
  public void compareRevLists(Map<UUID, TreeNode> list1, Map<UUID, TreeNode> list2, IReqURIResolver reslv);
  // searching of differences in descriptions
  public String compareRevDescription(TreeNode node, String isHTML, IReqURIResolver reslv, String basePath);
  public String compareRevDescription(TreeNode node);
}