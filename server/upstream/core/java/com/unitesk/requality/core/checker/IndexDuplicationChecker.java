package com.unitesk.requality.core.checker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.Index;

public class IndexDuplicationChecker implements INodeChecker {

  public IndexDuplicationChecker() {}

  @Override
  public boolean check(TreeNode node, List<IMark> result) {
    // Set<String> attrs = node.getAttributes().getAttributeKeys();
    if (node.isDisposed() || node.getType() == null || 
          node.isRoot() || node.getWorkParent() == null || 
          node.getWorkParent().isRoot() && !node.getId().startsWith("Req") || !node.getQualifiedId().startsWith(Requirement.getTypeRootQId()))
      return true;

    Map<String,TreeNode> indexes = new HashMap<>();
    Map<String, Set<TreeNode>> marked = new HashMap<>();
    for(TreeNode n: node.getWorkChildren()){
      String ind = "";
      Index i = n.getIndex();
      if(i!=null){
        ind = i.toString();
        if(!i.isUndefined()){
          TreeNode alt = indexes.get(ind);
          if(alt!=null){
            if(!marked.containsKey(ind)){
              marked.put(ind, new HashSet<>());
              marked.get(ind).add(alt);
            }
            marked.get(ind).add(n);
          }else
            indexes.put(ind, n);
        }
      }
      for(String s:marked.keySet()){
        Set<TreeNode> nodes = marked.get(s);
        String[]data = new String[nodes.size()+2];
        data[0] = null;
        int ia = 2;
        StringBuilder sb = new StringBuilder();
        for(TreeNode ta:nodes){
          if(sb.length() != 0)
            sb.append(", ");
          sb.append("'"+ta.getUserVisibleName()+"'");
          data[ia++] = ta.getQualifiedId();
        }
        data[0] = nodes.iterator().next().getIndex().toString();
        data[1] = sb.toString();
        for(TreeNode t:nodes)
          result.add(new CheckerTreeProblem(t, "problem_index_not_unique", "Index '%s' is not unique on some parent. Nodes with the same index: %s",
        		 data, nodes.toArray(new TreeNode[nodes.size()])));
      }
      if(!result.isEmpty())
        return false; 
    }
    return true;
  }
}
