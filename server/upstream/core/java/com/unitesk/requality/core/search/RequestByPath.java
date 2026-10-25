package com.unitesk.requality.core.search;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;

public class RequestByPath implements ISearchRequest{
  private Map<String,TypedValue> params;
  public RequestByPath(Map<String,TypedValue> params) {
    this.params = params;
  }

  @Override
  public boolean init(TreeNode node) {
	return true;
  }

  @Override
  public void test(TreeNode node, List<String> retAttrs) {
    TreeDB db = node.getTreeDB();
    TreeNode node2 = db.getNode(get("path"));
    if(node2 != null && node.getUUId().equals(node2.getUUId()))
      retAttrs.add("path");
  }

  @Override
  public boolean actionAndIgore(TreeNode root, List<Pair<? extends TreeNode, List<String>>> matches) {
    List<TreeNode> nds = Collections.emptyList();
    try {
      nds=root.getTreeDB().getLinkResolver().findNodesByPathWithDisposed(root, null, 
        root.getTreeDB(), get("path"), Path.QID|Path.UUID|Path.UV|Path.XPATH);
    } catch (NodeNotFoundException e){}
    for(TreeNode node:nds)
      matches.add(new Pair<>(node, Arrays.asList("path")));
//	       if(nds!=null)
//	         for(TreeNode nd:nds)
//	           if(nd != null && nd.getUUId().equals(root.getUUId()))
//	             return true;
    return true;
  }
  public String get(String string) {
    TypedValue tv = params.get(string);
    return tv == null ? null : tv.getGValue();
  }
  public void put(String name, TypedValue val){
    if(val == null)
      params.remove(name);
    else
      params.put(name, val);
  }

}
