package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.MapValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.exception.NullParentException;
import com.unitesk.requality.core.model.TreeNode;

public class StructuredIndexProvider implements IValueProvider {
  public static final String ATTR_KEY = TreeNode.ATTR_POSITION;
  public static String TYPE = "ChildIndexInParent";
  public static TypedValue ROOT_RESPONCE = new TypedValue(AttributeType.INT, -1);
  private static StructuredIndexProvider one = new StructuredIndexProvider();
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    TreeNode par  = null;
    try {
      par = as.getNode().getShownParentExc();
    }catch(NullParentException npe) {
    }
    if(par==null)
      return ROOT_RESPONCE;//root node
    else {
      MapValue childInd = (MapValue)
          par.attrs().getAttributeValue(ChildStructuredPositionsProvider.ATTR_KEY,null);
      if(childInd == null || childInd.getValues() == null)
        return ROOT_RESPONCE;//root node;
      return new TypedValue(AttributeType.INT, childInd.getValues().get(as.getNode().getSUUId()));
    }
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
    return AttributeType.INT;
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
