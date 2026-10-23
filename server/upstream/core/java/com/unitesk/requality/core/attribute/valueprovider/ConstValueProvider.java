package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.NodeMapper;

public class ConstValueProvider implements IValueProvider {
  ConstValueProvider(TypedValue val){
    this.tv = val;
  }
  protected void init(UUID origin, TypedValue tv, JSONObject setts){
    this.tv = tv;
  }
  private TypedValue tv;
  public static String TYPE="ConstValueProvider";
  public ConstValueProvider(AttributeType atype, Object value) {
    init(null,new TypedValue(atype, value),null);
  }

  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    return tv;
  }

  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return NULLDEP;
  }

//  @Override
//  public IValueProvider clone() {
//    return this;//TODO may it cause errors?
//    //new ConstValueProvider(tv.getType(),tv.getValue());
//  }

  @Override
  public TypedValue getRawValue() {
    return tv;
  }

  @Override
  public Map<String, Object> getRawStores() {
    return null;
  }
  @Override
  public boolean equals(Object obj) {
    if(!(obj instanceof ConstValueProvider))
      return false;
    ConstValueProvider cvp = (ConstValueProvider)obj;
    if(!cvp.tv.getType().equals(tv.getType()))
      return false;
    return Attribute.equalsValues(cvp.getRawValue(), getRawValue());
  }

  @Override
  public String getType() {
    return TYPE;
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
    NodeMapper.saveValue(vpo, tv);
  }
  @Override
  public AttributeType getAttributeType() {
    return tv.getType();
  }
}
