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

public class ParentProvider implements IValueProvider {
  public static String TYPE = "Parent";
  private static ParentProvider one = new ParentProvider();
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    return new TypedValue(AttributeType.TREENODE, as.getNode().getShownParentUUID());
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
  public void saveJSON(JSONObject vpo) throws JSONException {
    throw new UnsupportedOperationException();
  }

  @Override
  public AttributeType getAttributeType() {
    return AttributeType.TREENODE;
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
