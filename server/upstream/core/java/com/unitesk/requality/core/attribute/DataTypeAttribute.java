package com.unitesk.requality.core.attribute;

public class DataTypeAttribute{
  public DataTypeAttribute(String key, String desc, String atype) {
    this.key = key;
    this.description = desc;
    this.type = atype;
  }
  public String key;
  public String description;
  public String type;
}