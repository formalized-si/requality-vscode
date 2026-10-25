package com.unitesk.requality.core.search;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;

public class OrRequest implements ISearchRequest {

	private ISearchRequest[] reqs;
	public OrRequest(ISearchRequest ...  srs) {
		reqs = srs;
	}
	

	@Override
	public boolean init(TreeNode node) {
      boolean inited = false;
  	  for(ISearchRequest sr:reqs) {
  		boolean r = sr.init(node);
  		inited = inited || r;
  	  }
	  return inited;
	}

	@Override
	public void test(TreeNode node, List<String> baseAttrs) {
      List<String> res = new ArrayList<>();
      for(ISearchRequest sr:reqs) {
        List<String> itAttrs = new LinkedList<>(baseAttrs);
        sr.test(node, itAttrs);
        for(String s : itAttrs)
          if(!res.contains(s))
            res.add(s);
      }
      baseAttrs.clear();
      baseAttrs.addAll(res);
	}


	@Override
	public boolean actionAndIgore(TreeNode root, List<Pair<? extends TreeNode, List<String>>> matches) {
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
