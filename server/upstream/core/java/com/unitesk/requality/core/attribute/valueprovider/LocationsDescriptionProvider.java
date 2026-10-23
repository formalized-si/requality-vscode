package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Location;

public class LocationsDescriptionProvider implements IValueProvider {
  public static String TYPE = "LocationsDescriptionProvider";
  private TreeNode node;
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    try {
      //TODO add variables replace
      if(as.getNode().getType().equals(Location.ATTR_TYPE)) {
        String ldesc = as.getNode().getLocatonsDescription(
          as.getNode().getWorkParent().getNodeResourcesPath().toString());
        if(ldesc!=null)
          return new TypedValue(getAttributeType(), ldesc);
      }
    }catch(Exception e) {
      RLogger.logError(e);
    }
    return null;
  }
  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return Collections.emptySet();//TODO add descs
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public TypedValue getRawValue() {
    if(node!=null)
      try {
        String ldesc = node.getLocatonsDescription(null);
        if(ldesc!=null)
          return new TypedValue(getAttributeType(), ldesc); 
      }catch(Exception e) {
        RLogger.logError(e);
      }
    return null;
  }

  @Override
  public Map<String, Object> getRawStores() {
    return null;
  }

  private static LocationsDescriptionProvider k = new LocationsDescriptionProvider();
  public static IValueProvider getOne() {
    return k;
  }

  @Override
  public void attach(TreeNode node) {
    this.node = node;
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
    return AttributeType.HTML;
  }
}
