package com.unitesk.requality.core.search;

import java.util.Map;
import java.util.Set;

import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;

public class SearchByRequest implements ISearchRequest {
  private Map<String,TypedValue> params;
  JSFormulaHolder fh = null;

  public SearchByRequest(Map<String,TypedValue> params) {
	  this.params = params;
  }
  
  @Override
  public boolean init(TreeNode node) {
    fh = new JSFormulaHolder(node);
    return true;
  }

  @Override
  public boolean test(TreeNode node) {
    if(fh == null)
      throw new RuntimeException("JS holder not inited in search!");
    String f = params.get("formula").getGValue();
    JSFormula jf = fh.getRule(0);
    if(jf == null)
      jf = fh.addRule("Search rule", f);
    Pair<Object, Boolean> res = jf.runOnNode(node);
    return !JSFormulaHolder.resultNotTrue(res.getFirst());
  }

  @Override
  public boolean actionAndIgore(TreeNode root, Set<TreeNode> matches) {
    // TODO Auto-generated method stub
    return false;
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
