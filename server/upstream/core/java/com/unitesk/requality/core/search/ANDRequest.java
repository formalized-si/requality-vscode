package com.unitesk.requality.core.search;

import java.util.LinkedList;
import java.util.List;

import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.Pair;
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
	public void test(TreeNode node, List<String> baseAttrs) {
      List<String> res = new LinkedList<>();
      for(ISearchRequest sr:reqs) { 
        List<String> itAttrs = new LinkedList<>(baseAttrs); 
        sr.test(node, itAttrs);
        if(itAttrs.size() == 0)//one is not found
          return;
        for(String s : itAttrs)
          if(!res.contains(s))
            res.add(s);
      }
      baseAttrs.clear();
      baseAttrs.addAll(res);
      return;//collect all?
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
