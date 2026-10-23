package com.unitesk.requality.core.search;

import java.util.Set;
import com.unitesk.requality.core.attribute.TypedValue;
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
	public boolean test(TreeNode node) {
      return !req.test(node);
	}


	@Override
	public boolean actionAndIgore(TreeNode root, Set<TreeNode> matches) {
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
