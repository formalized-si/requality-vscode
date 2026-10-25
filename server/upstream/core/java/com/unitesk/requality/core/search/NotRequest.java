package com.unitesk.requality.core.search;

import java.util.List;
import java.util.Set;

import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;

public class NotRequest implements ISearchRequest {

	private ISearchRequest req;
	public NotRequest(ISearchRequest srs) {
		req = srs;
	}
	

	@Override
	public boolean init(TreeNode node) {
  	  return req.init(node);
	}

	@Override
	public void test(TreeNode node, List<String> foundAttrs) {
      Set<String> attrsAll = node.attrs().getAllAttributeKeys();//TODO check;
      //
      req.test(node, foundAttrs);//TODO think
      if(foundAttrs.isEmpty())
        foundAttrs.addAll(attrsAll);
      else
        foundAttrs.clear();
	}


	@Override
	public boolean actionAndIgore(TreeNode root, List<Pair<? extends TreeNode, List<String>>> matches) {
      return req.actionAndIgore(root, matches);
	}	
	@Override
	public String get(String string) {
      return null;
	}


	@Override
	public void put(String name, TypedValue val) {
	}

}
