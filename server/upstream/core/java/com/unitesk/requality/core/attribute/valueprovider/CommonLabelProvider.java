package com.unitesk.requality.core.attribute.valueprovider;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;

public class CommonLabelProvider implements IValueProvider {
  private static String TYPE = "CommonLabelProvider";
  private static Set<String> labelDeps = new HashSet<>();
  static {
    labelDeps.add(TreeNode.ATTR_NAMEORID);
  }
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    Attribute a = as.getAttribute(TreeNode.ATTR_NAMEORID);
    if(a!=null)
      return a.getTValue();
    return null;
  }

  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return labelDeps;
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
  }

  @Override
  public AttributeType getAttributeType() {
    return AttributeType.STRING;
  }
}
