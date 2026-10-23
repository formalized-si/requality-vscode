package com.unitesk.requality.core.attribute;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AttributeFilter implements IAttributeFilter {
  private static Set<AttributeType> sv = new HashSet<>();
  private static AttributeFilter one;
  static{
    sv = new HashSet<>(Arrays.asList(AttributeType.simpleValues()));
    one = new AttributeFilter();
  }
  private AttributeFilter(){
    
  }
  public static AttributeFilter getOne(){
    return one;
  }
  public boolean filter(Attribute a) {
    return !a.getKey().startsWith("_")
        && sv.contains(a.getType());
  }
}
