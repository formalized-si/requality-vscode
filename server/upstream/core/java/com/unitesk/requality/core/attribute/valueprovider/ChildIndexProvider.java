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

public class ChildIndexProvider implements IValueProvider {
  public static final String ATTR_KEY = TreeNode.ATTR_CHILD_INDEX;
  public static String TYPE = "ChildIndexInParent";
  public static TypedValue ROOT_RESPONCE = new TypedValue(AttributeType.INT, -1);
  private static ChildIndexProvider one = new ChildIndexProvider();
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    TreeNode par = as.getNode().getShownParent();
    if(par==null)
      return ROOT_RESPONCE;//root node
    else {
      Map<String, String> childInd = (Map<String, String>)
          par.attrs().getAttributeValue(ChildPositionsProvider.ATTR_KEY,Collections.emptyMap());
      return new TypedValue(AttributeType.INT, Integer.parseInt(childInd.getOrDefault(as.getNode().getSUUId(),"0")));
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
