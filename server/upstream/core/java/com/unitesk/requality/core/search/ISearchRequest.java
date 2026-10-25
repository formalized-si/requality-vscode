package com.unitesk.requality.core.search;

import java.util.List;

import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;

public interface ISearchRequest {
	
	boolean init(TreeNode node);

	void test(TreeNode node, List<String> foundAttrs);

	boolean actionAndIgore(TreeNode root, List<Pair<? extends TreeNode, List<String>>> matches);

	public String get(String string);
    public void put(String name, TypedValue val);
}