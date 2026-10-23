package com.unitesk.requality.core.search;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.exception.NodeNotFoundException;
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
  public boolean test(TreeNode node) {
      TreeDB db = node.getTreeDB();
      TreeNode node2 = db.getNode(get("path"));
      if(node2 != null && node.getUUId().equals(node2.getUUId()))
        return true;
      return false;
  }

  @Override
  public boolean actionAndIgore(TreeNode root, Set<TreeNode> matches) {
    List<TreeNode> nds = Collections.emptyList();
    try {
      nds=root.getTreeDB().getLinkResolver().findNodesByPathWithDisposed(root, null, 
        root.getTreeDB(), get("path"), Path.QID|Path.UUID|Path.UV|Path.XPATH);
    } catch (NodeNotFoundException e){}
    matches.addAll(nds);
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
