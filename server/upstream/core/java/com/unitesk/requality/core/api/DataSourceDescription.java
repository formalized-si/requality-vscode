package com.unitesk.requality.core.api;

import com.unitesk.requality.core.attribute.AttributeType;

public class DataSourceDescription{
  public String id;
  public String dataType;
  public String description;
  public boolean isPaged;
  public DataSourceDescription(String id, AttributeType at, String description, boolean isPaged) {
    this(id, at.name(), description, isPaged);
  }

  public DataSourceDescription(String id, String at, String description, boolean isPaged) {
    this.id = id;
    this.dataType = at;
    this.description = description;
    this.isPaged = isPaged;
  }
}
