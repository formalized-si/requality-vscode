package com.unitesk.requality.core.attribute.valueprovider;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportFolder;
import com.unitesk.requality.nodetype.ReportSettings;

public class RepoLocalProvider extends TypeFilteredProvider implements IAttributePropogationProvider{
  private static String TYPE = "RepoNodeState";
  private static Set<String> allowedTypes;
  private UUID targetUuid;
  static{
    allowedTypes = new HashSet<>();
    allowedTypes.add(DocFolder.TYPE_NAME);
    allowedTypes.add(Document.TYPE_NAME);
    allowedTypes.add(ReportData.TYPE_NAME);
    allowedTypes.add(ReportSettings.TYPE_NAME);
    allowedTypes.add(ReportFolder.TYPE_NAME);
  }
  public RepoLocalProvider(UUID uuid) {
    this.targetUuid = uuid;
  }

  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    
    TreeNode par = as.getNode().getWorkParent();
    if(par!=null)
      if(!as.getNode().getUUId().equals(targetUuid) &&
          par.attrs().getBooleanAttributeValue(TreeNode.ATTR_REPO_STATE))
        return new TypedValue(AttributeType.BOOL, true);
    try{
      if(Global.getRepoManager(as.getNode().getTreeDB()).getIsDirectlyIgnored(par, as.getNode(), as.getNode().getId(), true))
        return new TypedValue(AttributeType.BOOL, true);
    }catch(Exception r){
      r.printStackTrace();
      return null;
    }
    return null;
  }

  //private static RepoLocalProvider vp = new RepoLocalProvider(); 
  private static Set<String> nameDep = new HashSet<>();
  static {
    nameDep.add(TreeNode.ATTR_NAME);
    nameDep.add(TreeNode.ATTR_ID);
  }

  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return nameDep;
  }

  @Override
  public String getType() {
    // TODO Auto-generated method stub
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

  @Override
  public void attach(TreeNode node) {
  }

  @Override
  public void detach(TreeNode node) {
  }

  @Override
  public void saveJSON(JSONObject vpo) throws JSONException {
    throw new UnsupportedOperationException();
  }
  @Override
  public AttributeType getAttributeType() {
    return AttributeType.BOOL;
  }
  @Override
  protected Set<String> getSuitableTypes() {
    return allowedTypes;
  }

  @Override
  public AttributeAvailability getScope() {
    return AttributeAvailability.SUBTREE;
  }

  public static IValueProvider getOne(UUID node) {
    return new RepoLocalProvider(node);
  }
}
