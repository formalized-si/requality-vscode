package com.unitesk.requality.core.attribute.valueprovider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.propagator.UVIDPropagator;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.repo.RevisionInfo;

public class UserVisibleIdProvider implements IValueProvider {
  private static String TYPE = "UserVisibleIdProvider";
  private void getValue(AttributeStorage as, StringBuilder sb){
    getValue(as, sb, true);
  }
  private void getValue(AttributeStorage as, StringBuilder sb, boolean first){
    //StringBuilder sb = new StringBuilder();
    String name = as.getNode().getName();
    if (name != null) {
      Boolean ignore = as.getBooleanAttributeValue(TreeNode.ATTR_IGNORE_NAME);
      if (!ignore) {
        sb.append(name);
        return;
      }
    }
    TreeNode parent = as.getNode().getWorkParent();
    if (parent == null || parent.getType().equals("DBRoot")) {
      sb.append(as.getNode().getNameOrId()); 
      return;
    } else if(parent.isVRoot()){
      sb.append(as.getNode().getNameOrId());
      return;
    }else{
      int k = sb.length();
      if(parent.getType().equals(VirtualNode.TYPE_NAME)&&
          ((VirtualNode)parent).isCollapsed())//dealing with vnode collapsing
        parent = parent.getWorkParent();
      boolean ignore = false;
      if (parent.getWorkParent() == null || 
          parent.getWorkParent().getType().equals("DBRoot")) {
        ignore = true;
      }
      boolean isreq = parent.getType().equals(Requirement.TYPE_NAME);
      String val = parent.getUserVisibleName(true);
      if(!ignore||!isreq){
        sb.append(val);
      }
      //sb.append(parent.getUserVisibleName(true));
      //parent.getUserVisibleName(inner, false, sb);
      if (sb.length() != k) sb.append("/");
    }
    // userFriendlyName = userFriendlyName.substring(c);
    if(as!=null && as.getNode()!=null)
      sb.append(as.getNode().getNameOrId());
  }
  
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    StringBuilder sb = new StringBuilder();
    getValue(as, sb);
    return new TypedValue(AttributeType.STRING, sb.toString());
  }
  private static Set<String> deps = new HashSet<>();
  static{
    deps.add(TreeNode.ATTR_LABEL);
    deps.add(TreeNode.ATTR_IGNORE_NAME);
    deps.add(TreeNode.ATTR_PARENT);
    deps.add(VirtualNode.ATTR_COLLAPSEVNODE);
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
  public Map<String, Object> getRawStores() {
    return null;
  }
  private static String USER_VISIBLE_ID_PROPOGATOR = "USERVISIBLE_ID_PROVIDERS";
  private static INodeChangeListener nchl = new INodeChangeListener(){
    
    @Override
    public void moved(TreeNode node, TreeNode oldParent) {
      TreeDB db = node.getTreeDB();
      if(db.isVirtual())
        db = Instantiator.getRootTree(db);
      UUID uid = node.getUUId();

      String treeId = getTreeId(db);
      Map<UUID, UVIDPropagator> map = db.getMetaObject(USER_VISIBLE_ID_PROPOGATOR);
      if(map!=null && map.containsKey(uid)){
        AttributeDefinition ad = node.getVirtualAttributeDefinition(TreeNode.ATTR_USERVISIBLEID);
        ad.getValueProvider().detach(node);
        ad.getValueProvider().attach(node);
        updateValue(node,false,true);//TODO true?
      }
    }
  
	@Override
    public void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m) {
      // TODO Auto-generated method stub
      
    }
  
    @Override
    public void deleted(TreeNode source) {
      TreeDB db = source.getTreeDB();
      if(db.isVirtual())
        db = Instantiator.getRootTree(db);
      UUID uid = source.getUUId();
      String treeId = getTreeId(db);
      Map<UUID, UVIDPropagator> map = db.getMetaObject(USER_VISIBLE_ID_PROPOGATOR);
      if(map!=null && map.containsKey(uid)){
        TreeNode par = source.getWorkParent();
        if(par!=null&&!par.isDisposed())
          par.removeAPropogator(map.get(uid));
        map.remove(uid);
      }
    }
  
    @Override
    public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
    }
  
    private void updateValue(TreeNode node,boolean andname,boolean uvch) {
      if(!andname)
      {
        String name = node.getName();
        if(name != null){
          boolean ignorename = node.attrs().getBooleanAttributeValue(TreeNode.ATTR_IGNORE_NAME,false);
          if(!ignorename)
            return;//nothing changed
        }
      }
    }

    @Override
    public void attributeChange(TreeNode source, String what, Attribute old) {
    }
  };
  @Override
  public void attach(TreeNode node) {
    TreeDB db = node.getTreeDB();
    if(db.isVirtual())
      db = Instantiator.getRootTree(node.getTreeDB());
    String treeId = getTreeId(db);
    Map<UUID, UVIDPropagator> map = db.getMetaObject(USER_VISIBLE_ID_PROPOGATOR);
    if(map == null) {
      synchronized (db) {
        map = db.getMetaObject(USER_VISIBLE_ID_PROPOGATOR);
        if(map == null) {
          map = new HashMap<>();
          db.registerMetaObject(USER_VISIBLE_ID_PROPOGATOR, map);
        }
      }
    }
    node.addChangeListener(nchl);
    if(!map.containsKey(node.getUUId())){
      map.put(node.getUUId(),new UVIDPropagator(node));
      if(node.getWorkParent()!=null)
        node.getWorkParent().addAPropogator(map.get(node.getUUId()));
    }
  }
  private static String getTreeId(TreeDB db) {
    return db.getLocalName()+"-"+RevisionInfo.toString(db, db.revision);
  }

  @Override
  public void detach(TreeNode node) {
    TreeDB db = Instantiator.getRootTree(node.getTreeDB());
    node.removeChangeListener(nchl);

    String treeId = getTreeId(db);
    Map<UUID, UVIDPropagator> map = db.getMetaObject(USER_VISIBLE_ID_PROPOGATOR);
    if(node.getWorkParent()!=null)
      node.getWorkParent().removeAPropogator(map.get(node.getUUId()));
    if(map!=null)
      map.remove(node.getUUId());
  }
  private static IValueProvider one = new UserVisibleIdProvider();
  public static IValueProvider getOne() {
    return one;
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
