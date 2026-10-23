package com.unitesk.requality.core.operation.create;

import com.unitesk.requality.core.model.TreeNode;

public class CreateNode {
  public static TreeNode call(TreeNode par, String type, String id){
    return CreateElementOperation.execute(true, par, id, type, true, null);
  }
  public static TreeNode call(boolean ignoreTrans, TreeNode par,String id, String type, String templ, boolean nocheck, TreeNode nodeAfter){
    CreateElementOperation ceo = CreateElementOperation.getHandlersMap().get(type);
    if(ceo == null)
      return null;//TODO proper
    return ceo.execute(ignoreTrans, par, id, templ, type, nocheck, nodeAfter);
  }
}
