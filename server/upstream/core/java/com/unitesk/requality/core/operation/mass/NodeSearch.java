package com.unitesk.requality.core.operation.mass;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.Pair;
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
  

  List<Pair<? extends TreeNode, List<String>>> matches = new LinkedList<>();

  @Override
  public void performSingleAction(TreeNode node) {
    List<String> attrs = new LinkedList<>();
    sr.test(node, attrs);
    if(attrs.size()>0) {
      matches.add( new Pair<>(node, attrs));
    }
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
  public List<Pair<? extends TreeNode,  List<String>>> getMatches() {
    return matches;
  }
}
