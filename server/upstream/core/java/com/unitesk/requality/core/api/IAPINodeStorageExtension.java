package com.unitesk.requality.core.api;

import java.util.Map;
import java.util.UUID;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeDefinition;

public interface IAPINodeStorageExtension {
  public void actionsInNodeDefinition(UUID node, Map<String, AttributeDefinition> attrs, JSONObject nodeObject);
  public void actionsInAttributeDefinition(UUID node, AttributeDefinition attrs, JSONObject attributeObject);
}
