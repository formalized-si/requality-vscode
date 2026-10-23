package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.model.TreeNode;

public interface IGenericValueProvider {
  static Set<String> NULLDEP = Collections.emptySet();
  Set<String> getDependencies(AttributeStorage as);
  String getType();
  //IValueProvider clone();
  Map<String, Object> getRawStores();
  void attach(TreeNode node);
  void detach(TreeNode node);
  void saveJSON(JSONObject vpo) throws JSONException;
  AttributeType getAttributeType();
}
