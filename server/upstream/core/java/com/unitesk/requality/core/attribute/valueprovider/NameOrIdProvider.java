package com.unitesk.requality.core.attribute.valueprovider;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;

public class NameOrIdProvider implements IValueProvider {
  private static String TYPE = "NameOrId";

  private String getNameOrId(TreeNode node) {
    //if (!inner && node.getId().equals("Requirements")) return "";
    Object val = node.getName();
    if (val == null) return node.getId();
    return val.toString();
  }
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    return new TypedValue(AttributeType.STRING, getNameOrId(as.getNode()));
  }

  private static IValueProvider vp = new NameOrIdProvider(); 
  public static IValueProvider getOne() {
    return vp;
  }
  private static Set<String> nameDep = new HashSet<>();
  static {
    nameDep.add(TreeNode.ATTR_NAME);
    nameDep.add(TreeNode.ATTR_ID);
  }

  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return nameDep;
  }

  @Override
  public String getType() {
    // TODO Auto-generated method stub
    return TYPE;
  }

  @Override
  public TypedValue getRawValue() {
    return null;
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

  @Override
  public void saveJSON(JSONObject vpo) throws JSONException {
    throw new UnsupportedOperationException();
  }
  @Override
  public AttributeType getAttributeType() {
    return AttributeType.STRING;
  }
}
