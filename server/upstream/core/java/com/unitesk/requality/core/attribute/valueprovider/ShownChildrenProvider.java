package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.Sorting;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.core.sorting.TreeNodeComparator;

public class ShownChildrenProvider implements IValueProvider {
  public static final String ATTR_KEY = TreeNode.ATTR_SHOWN_CHILDREN;
  public static String TYPE = "ShownChildren";
  private static TypedValue EMPTY_VALUE = new TypedValue(AttributeType.LIST, new ListValue(AttributeType.TREENODE, Collections.emptyList()));
  private static ShownChildrenProvider one = new ShownChildrenProvider();
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    List<Object> uids = Sorting.formShownChilren(as.getNode(), 
        TreeNodeComparator.getComparator(as.getNode().getTreeDB()), false, false, true);
    if(uids.isEmpty())
      return EMPTY_VALUE;
    return new TypedValue(AttributeType.LIST, new ListValue(AttributeType.TREENODE, uids));
  }

  private static Set<String> deps = new HashSet<>();
  static{
    deps.add(TreeNode.ATTR_CHILDREN_UUIDS);
    deps = Collections.unmodifiableSet(deps);
  }
  
  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return getDeps(as.getNode(),as.getNode().getTreeDB().getSortingSettings());
  }

  private static Set<String> getDeps(TreeNode node, SortingSettings sortingSettings) {
    //TODO sorting settings
    return deps;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public TypedValue getRawValue() {
    return null;
  }

  @Override
  public void saveJSON(JSONObject vpo) throws JSONException {
    throw new UnsupportedOperationException();
  }

  @Override
  public AttributeType getAttributeType() {
    return AttributeType.LIST;
  }

  @Override
  public Map<String, Object> getRawStores() {
    return null;
  }

  @Override
  public void attach(TreeNode node) {
    
  }

  @Override
  public void detach(TreeNode node) {
    
  }

  public static IValueProvider getOne() {
    return one;
  }
}
