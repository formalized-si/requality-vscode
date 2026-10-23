package com.unitesk.requality.core.search;

import java.util.Set;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;

public class ANDRequest implements ISearchRequest {

	private ISearchRequest[] reqs;
	public ANDRequest(ISearchRequest ...  srs) {
		reqs = srs;
	}
	

	@Override
	public boolean init(TreeNode node) {
      boolean inited = false;
  	  for(ISearchRequest sr:reqs)
  		inited = inited && sr.init(node);
	  return inited;
	}

	@Override
	public boolean test(TreeNode node) {
      boolean res = false;
  	  for(ISearchRequest sr:reqs)
  		res = res && sr.test(node);
	  return res;
	}


	@Override
	public boolean actionAndIgore(TreeNode root, Set<TreeNode> matches) {
      boolean res = false;
  	  for(ISearchRequest sr:reqs)
  		res = res && sr.actionAndIgore(root,matches);
	  return res;
	}	
	@Override
	public String get(String string) {
      return null;
	}


	@Override
	public void put(String name, TypedValue val) {
	}

}
