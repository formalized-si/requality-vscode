package com.unitesk.requality.core.attribute.valueprovider;

public class ReferenceRawValue{
  public String node, version, value;
  public ReferenceRawValue(String value, String node, String vers) {
    this.value = value;
    this.node = node;
    this.version = vers;
  }
}