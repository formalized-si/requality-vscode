package com.unitesk.requality.core.search;

import java.util.Set;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;

public interface ISearchRequest {
	
	boolean init(TreeNode node);

	boolean test(TreeNode node);

	boolean actionAndIgore(TreeNode root, Set<TreeNode> matches);

	public String get(String string);
    public void put(String name, TypedValue val);
}