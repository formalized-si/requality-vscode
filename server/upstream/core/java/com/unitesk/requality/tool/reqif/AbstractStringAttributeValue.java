package com.unitesk.requality.tool.reqif;

public abstract class AbstractStringAttributeValue extends AbstractAttributeValue {
  public String value;

  public AbstractStringAttributeValue(AttributeDefinition definition, String suffix) {
    super(definition, suffix);
  }

  @Override
  public String getString() {
    return value;
  }
}
