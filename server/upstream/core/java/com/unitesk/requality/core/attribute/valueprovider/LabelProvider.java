package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Map;
import java.util.Set;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;

public class LabelProvider implements IValueProvider {
  public static String TYPE = "LabelProvider";
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    try {
      return as.getNode().getLabelVP().getTypedValue(key, as);
    }catch(Exception e) {
      RLogger.logError(e);
      return null;
    }
  }

  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return as.getNode().getLabelVP().getDependencies(as);
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

  private static LabelProvider k = new LabelProvider();
  public static IValueProvider getOne() {
    return k;
  }

  @Override
  public void attach(TreeNode node) {
    node.getLabelVP().attach(node);
  }

  @Override
  public void detach(TreeNode node) {
    node.getLabelVP().detach(node);
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
