package com.unitesk.requality.core.attribute;

import com.unitesk.requality.core.model.TreeNode;

public interface IReqURIResolver {
  public static IReqURIResolver EMPTY = new IReqURIResolver() {
	@Override
	public ReqURIResolveResult resolve(TreeNode node, String path, String value) {
      return ReqURIResolveResult.NF;
	}
  };
  public ReqURIResolveResult resolve(TreeNode node, String path, String value);
}
