package com.unitesk.requality.tool.reqif;

import java.util.Map;

public interface EntityWithAttributeDefs {

  AttributeDefinition getAttributeDefinition(String dval);
  public Map<String, AttributeDefinition> getAttributesMap();
}
