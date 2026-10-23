package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Map;
import java.util.Set;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;

public class NodeResPathVProvider implements IValueProvider {
  public static String TYPE="NodeResValueProvider";
  private String key = null;
  NodeResPathVProvider(String key) {
    this.key = key;
  }
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    String path = as.getNode().getNodeResourcesPath().toString();
    if(key!=null)
      path = path+"/"+this.key;
    return new TypedValue(AttributeType.STRING, path);
  }

  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return NULLDEP;
  }
  private static IValueProvider vp = new NodeResPathVProvider(null); 
  public static IValueProvider getOne(String key) {
    if(key == null)
      return vp;
    else 
      return new NodeResPathVProvider(key);
  }
  @Override
  public String getType() {
    return TYPE;
  }
  @Override
  public TypedValue getRawValue() {
    // TODO Auto-generated method stub
    return null;
  }
  @Override
  public Map<String, Object> getRawStores() {
    // TODO Auto-generated method stub
    return null;
  }
  @Override
  public void attach(TreeNode node) {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void detach(TreeNode node) {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void saveJSON(JSONObject vpo) throws JSONException {
    throw new UnsupportedOperationException();
  }

  @Override
  public AttributeType getAttributeType() {
    return AttributeType.STRING;
  }
//  @Override
//  public NodeResPathVProvider clone(){
//    // TODO Auto-generated method stub
//    return this;
//  }
}