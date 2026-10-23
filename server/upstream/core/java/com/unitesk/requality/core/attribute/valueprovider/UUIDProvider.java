package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;

public class UUIDProvider implements IValueProvider {
  public static final String ATTR_UUID = "_uuid";
  private static String TYPE = "UUIDProvider";
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    return new TypedValue(getAttributeType(), as.getNode().getUUId());
  }
  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return Collections.emptySet();
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
  private static IValueProvider one = new UUIDProvider();
  public static IValueProvider getOne() {
    return one;
  }
  @Override
  public void saveJSON(JSONObject vpo) throws JSONException {
    throw new UnsupportedOperationException();
  }
  @Override
  public AttributeType getAttributeType() {
    return AttributeType.TREENODE;
  }
}
