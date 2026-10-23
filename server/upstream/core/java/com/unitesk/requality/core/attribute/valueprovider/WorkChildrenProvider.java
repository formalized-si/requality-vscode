package com.unitesk.requality.core.attribute.valueprovider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;

public class WorkChildrenProvider implements IValueProvider {
  public static String TYPE = "WorkChildren";
  private static WorkChildrenProvider one = new WorkChildrenProvider();
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    List<Object> workChilds = (List<Object>)as.getListAttributeValue(TreeNode.ATTR_CHILDREN_UUIDS);
    Set<Object> uids = workChilds!=null?new TreeSet<>(workChilds):Collections.emptySet();
    List<Object> val = new ArrayList<>();
    for(Object o:uids){
      UUID uid = (UUID) o;
      TreeNode node = as.getNode().getTreeDB().getNode(uid);
      if(node!=null)
        val.add(node.getUUId());
      else
        RLogger.logError("No node found for uid "+uid);
    }
    return new TypedValue(AttributeType.LIST, new ListValue(AttributeType.TREENODE, val));
  }

  private static Set<String> deps = new HashSet<>();
  static{
    deps.add(TreeNode.ATTR_CHILDREN_UUIDS);
    deps = Collections.unmodifiableSet(deps);
  }

  @Override
  public Set<String> getDependencies(AttributeStorage as) {
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
