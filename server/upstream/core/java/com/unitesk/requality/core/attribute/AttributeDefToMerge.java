package com.unitesk.requality.core.attribute;

public class AttributeDefToMerge {

  public boolean clone;
  public AttributeDefinition attr;
  public MergeParams param;

  public AttributeDefToMerge(AttributeDefinition attr, MergeParams par, boolean clone) {
    this.attr = attr;
    param = par;
    this.clone = clone;
  }
}
