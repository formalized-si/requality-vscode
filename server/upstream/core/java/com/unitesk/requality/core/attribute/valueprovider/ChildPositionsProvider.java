package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.MapValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.exception.NullParentException;
import com.unitesk.requality.core.model.TreeNode;

public class ChildPositionsProvider implements IValueProvider {
  public static final String ATTR_KEY = "_childrenPositions";
  public static String TYPE = "ChildPositions";
  private static ChildPositionsProvider one = new ChildPositionsProvider();
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
	TreeNode par = null;
	try {
      par = as.getNode().getShownParentExc();
	}catch(NullParentException npe) {
	}
    if(par==null)
      return MapValue.EMPTY_ANSWERS.get(AttributeType.INT);//root node;
    else {
      List<Object> ln = as.getNode().attrs().getListAttributeValue(ShownChildrenProvider.ATTR_KEY);
      if(ln == null)
        return null;
      Map<String, Object> pos = ln==null||ln.size()==0?Collections.emptyMap():new HashMap<>();
      int i = 0;
      for(Object ob:ln) {
        UUID uid = (UUID) ob;
        pos.put(uid.toString(), i++);
      }
      return pos.isEmpty()?MapValue.EMPTY_ANSWERS.get(AttributeType.INT): new TypedValue(AttributeType.MAP, new MapValue(AttributeType.INT, pos));//root node
    }
  }
  
  private static Set<String> deps = new HashSet<>();
  static{
    deps.add(TreeNode.ATTR_SHOWN_CHILDREN);
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
    return AttributeType.MAP;
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
