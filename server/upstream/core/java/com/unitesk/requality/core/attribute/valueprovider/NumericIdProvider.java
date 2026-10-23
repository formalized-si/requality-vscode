package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;

public class NumericIdProvider implements IValueProvider {
  public static final String ATTR_KEY = "_numericId";
  public static String TYPE = "NumericId";
  private static NumericIdProvider one = new NumericIdProvider();
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    TreeNode par = as.getNode().getShownParent();
    if(par==null || par.getShownParent() == null)
      return new TypedValue(AttributeType.STRING, "");//root node
    else {
      int pos = as.getNode().attrs().getIntAttributeValue(StructuredIndexProvider.ATTR_KEY, -1);
      String numid = par.attrs().getStringAttributeValue(ATTR_KEY,"");
      return new TypedValue(AttributeType.STRING, (numid.isEmpty()?"":(numid+"."))+(pos+1));//root node
    }
  }
  
  private static Set<String> deps = new HashSet<>();
  static{
    deps.add(TreeNode.ATTR_PARENT);
    deps.add(StructuredIndexProvider.ATTR_KEY);
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
    return AttributeType.STRING;
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
