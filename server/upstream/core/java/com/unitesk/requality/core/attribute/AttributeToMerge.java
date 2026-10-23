package com.unitesk.requality.core.attribute;

public class AttributeToMerge {
  public boolean clone;
  public Attribute attr;
  public MergeParams param;

  public AttributeToMerge(Attribute attr, MergeParams par, boolean clone) {
    this.attr = attr;
    param = par;
    this.clone = clone;
  }
}
