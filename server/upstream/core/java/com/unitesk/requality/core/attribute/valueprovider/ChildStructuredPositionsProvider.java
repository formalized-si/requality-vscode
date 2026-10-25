package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Collection;
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
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;

public class ChildStructuredPositionsProvider implements IValueProvider {
  public static final String ATTR_KEY = "_childrenStructuredPositions";
  public static String TYPE = "ChildStructuredPositions";
  private static ChildStructuredPositionsProvider one = new ChildStructuredPositionsProvider();
  public static boolean checkIsStructural(TreeDB db, ITreeStorage storage, Collection<Object> childs) {
    //TODO coreutils isHeader check?
    for(Object chi:childs)
    {
      UUID uid = (UUID)chi;
      String type = storage.getNodeRawType(uid);
      if(type == null){
        type = db.getNodeType(uid);
      }
      //null if already removed
      if(type!=null && (type.equals(Requirement.TYPE_NAME) || type.equals(TextNode.TYPE_NAME) || type.equals(TestPurpose.TYPE_NAME)))//TODO check
        return true;
    }
	return false;
  }
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
	TreeNode par = null;
	try {
      par = as.getNode().getShownParentExc();
	}catch(NullParentException npe) {
	}
    if(par==null)
      return MapValue.EMPTY_ANSWERS.get(AttributeType.INT);//root node
    else {
      List<Object> ln = as.getNode().attrs().getListAttributeValue(ShownChildrenProvider.ATTR_KEY);
      if(ln == null)
        return null;//TODO possible?
      Map<String, Object> pos = ln==null||ln.size()==0?Collections.emptyMap():new HashMap<>();
      int i = 0;
      TreeDB db = as.getNode().getTreeDB();
      ITreeStorage storage = db.getStorage();
      for(Object ob:ln) {
        UUID uid = (UUID) ob;
        TreeNode chi = db.getCachedNodeOrVirt(uid);
        Collection childs = chi == null?storage.getChildren(uid):chi.getActiveWorkChildrenUUIDs();
        if(checkIsStructural(db, storage, childs))
          pos.put(uid.toString(), i++);
        else
          pos.put(uid.toString(),-1);
      }
      return pos.isEmpty() ?  MapValue.EMPTY_ANSWERS.get(AttributeType.INT): new TypedValue(AttributeType.MAP, new MapValue(AttributeType.INT, pos));//root node
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
