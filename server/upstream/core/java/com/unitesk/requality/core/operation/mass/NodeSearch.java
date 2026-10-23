package com.unitesk.requality.core.operation.mass;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.search.ISearchRequest;
import com.unitesk.requality.core.search.SearchMethod;
import com.unitesk.requality.core.search.SearchRequestMan;

public class NodeSearch extends AbstractMassOperation {
  private ISearchRequest sr;
  public NodeSearch() {
    this(null);
    setIgnoreTransaction(true);
  }
  public NodeSearch(ISearchRequest sr) {
    super("SearchNode");
    this.sr = sr;
    setIgnoreTransaction(true);
  }

  @Override
  public String getName() {
    return "Search Node";
  }

  @Override
  public boolean execute(TreeNode root) {
     matches.clear();
     if(sr == null)
       return false;
     sr.init(root);
     if(!sr.actionAndIgore(root, matches))
       return super.execute(root);
	return false;
  }
  

  Set<TreeNode> matches = new HashSet<>();

  @Override
  public void performSingleAction(TreeNode node) {
    if(sr.test(node))
      matches.add(node);
  }

  @Override
  public void applySettings(Map<String, String> args) {
	SearchMethod sm = SearchRequestMan.loadMethod(args);
	Map<String, TypedValue> map = SearchRequestMan.fromStored(sm, args);
    ISearchRequest sr = SearchRequestMan.restore(SearchRequestMan.toStored(
    		sm, map));
    if(sr != null)
      this.sr = sr;
  }
  public Collection<? extends TreeNode> getMatches() {
    return matches;
  }
}
