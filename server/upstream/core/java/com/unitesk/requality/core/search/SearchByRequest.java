package com.unitesk.requality.core.search;

import java.util.List;
import java.util.Map;

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
  public void test(TreeNode node, List<String> attrsFound) {
    if(fh == null)
      throw new RuntimeException("JS holder not inited in search!");
    String f = params.get("formula").getGValue();
    JSFormula jf = fh.getRule(0);
    if(jf == null)
      jf = fh.addRule("Search rule", f);
    Pair<Object, Boolean> res = jf.runOnNode(node);
    if(!JSFormulaHolder.resultNotTrue(res.getFirst()))
     attrsFound.add("formula: "+jf);
  }

  @Override
  public boolean actionAndIgore(TreeNode root, List<Pair<? extends TreeNode, List<String>>> matches) {
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
