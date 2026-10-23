package com.unitesk.requality.report;

import com.unitesk.requality.report.template.TemplateScope;

public class Group {
  
  String id;
  StringBuilder description = new StringBuilder();
  String name;
  String parent;
  public TemplateScope source;
  int order;
  public Group(String id2, String rname, String parent2, int order, TemplateScope source) {
    id = id2;
    name = rname;
    parent = parent2;
    this.order = order;
    this.source = source;
  }
  public String getId() {
    return id;
  }
  public String getName() {
    return name;
  }
  public String getSource() {
    return source.toString();
  }
  public String getParent() {
    return parent;
  }
  public int getOrder() {
    return order;
  }
  public String getDescription() {
    return description.toString();
  }
  @Override
  public String toString() {
    return name+"["+id+"] ("+order+") "+description.substring(0,Math.min(10, description.length()));
  }
}
