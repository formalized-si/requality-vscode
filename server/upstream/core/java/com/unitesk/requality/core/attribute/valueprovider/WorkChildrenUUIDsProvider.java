package com.unitesk.requality.core.attribute.valueprovider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.events.DefaultNodeChangeListener;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;

public class WorkChildrenUUIDsProvider implements IValueProvider {
  public static String TYPE = "WorkChildrenUUIDs";
  public static WorkChildrenUUIDsProvider one = new WorkChildrenUUIDsProvider();
  private static TypedValue EMPTY_VALUE=new TypedValue(AttributeType.LIST, new ListValue(AttributeType.TREENODE, Collections.emptyList()));
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    List<UUID> uids = Collections.emptyList();
    List<Object> val = Collections.emptyList();
    if(as.getNode()!=null){
      uids = as.getNode().getWorkChildrenUUIDs();
      for(UUID uid:uids){
        if(val.isEmpty())
          val = new ArrayList<>();
        val.add(uid);
        //TreeNode node = as.getNode().getTreeDB().getNode(uid);
        //if(node!=null)
          //val.add(node.getUUId());
      }
    }
    if(val.isEmpty())
    	return EMPTY_VALUE;
    return new TypedValue(AttributeType.LIST, new ListValue(AttributeType.TREENODE, val));
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
  public Map<String, Object> getRawStores() {
    return null;
  }
  private static class WCListener extends DefaultNodeChangeListener{
    private TreeNode node;
    public WCListener(TreeNode targ){
      node = targ;
    }
    @Override
    public void attributeChange(TreeNode source, String what, Attribute old) {
      if(what.equals(TreeNode.ATTR_CHILDREN_UUIDS))
      {
        node.updateLocalAttribute(TreeNode.ATTR_CHILDREN_UUIDS);//TODO only on change?
//        node.commitAttributes();
      }
    }
  }
  //private static Map<TreeNode, INodeChangeListener> smap = new HashMap<>();
  private static String META_NODE_WC_LISTENERS_MAP="WORK_CHILDREN_LISTENER_MAP";
  private INodeChangeListener getListener(TreeNode node){
    if(node == null)
      return null;
    TreeDB db = node.getTreeDB();
    Map<TreeNode, INodeChangeListener> map = db.getMetaObject(META_NODE_WC_LISTENERS_MAP);
    if(map == null)
      synchronized (META_NODE_WC_LISTENERS_MAP) {
        map = db.getMetaObject(META_NODE_WC_LISTENERS_MAP);
        if(map == null)
        {
          map = new HashMap<>();
          db.registerMetaObject(META_NODE_WC_LISTENERS_MAP, map);
        }
      }
    INodeChangeListener ret = map.get(node);
    if(ret==null)
    {
      synchronized (map) {
        ret = map.get(node);
        if(ret == null) {
          ret = new WCListener(node);
          map.put(node, ret);
        }
      }
    }
    return ret;
  }

  @Override
  public void attach(TreeNode node) {
    //this.node = node;
    if(node.getCloneParent()!=null){
      node.getCloneParent().addChangeListener(getListener(node));
    }
  }

  @Override
  public void detach(TreeNode node) {
    //this.node = null;
    TreeNode cp = node.getCloneParent();
    if(cp!=null){
      cp.removeChangeListener(getListener(node));
      TreeDB db = node.getTreeDB();
      Map<TreeNode, INodeChangeListener> map = db.getMetaObject(META_NODE_WC_LISTENERS_MAP);
      if(map!=null)
        map.remove(node);
    }
  }

  @Override
  public void saveJSON(JSONObject vpo) throws JSONException {
    throw new UnsupportedOperationException();
  }

  @Override
  public AttributeType getAttributeType() {
    return AttributeType.LIST;
  }

  public static IValueProvider getOne() {
    return one;
  }
}
