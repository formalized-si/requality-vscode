package com.unitesk.requality.core.attribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class EnumDefinitionValue implements Iterable<EnumValue>, Cloneable {
  private AttributeType type;
  private List<EnumValue> values;

  public EnumDefinitionValue(AttributeType atype, List<EnumValue> values) {
    this.type = atype;
    this.values = values;
  }

  public AttributeType getType() {
    return type!=null?type:AttributeType.STRING;//adds default
  }

  public List<EnumValue> getValues() {
    return values;
  }

  @Override
  public Iterator<EnumValue> iterator() {
    return new Iterator<EnumValue>() {
      int ind = 0;

      @Override
      public void remove() {
        values.remove(ind);
      }

      @Override
      public EnumValue next() {
        return values.get(ind++);
      }

      @Override
      public boolean hasNext() {
        return ind < values.size();
      }
    };
  }

  public boolean containsValue(Object nowval) {
    for (EnumValue ev : values) if (ev.equals(nowval)) return true;
    // TODO optimization
    return false;
  }

  public EnumValue addEnumValue(Object val, String comm) {
    EnumValue ev = new EnumValue(val, comm);
    if (values.add(ev)) return ev;
    return null;
  }

  public void addEnumValue(EnumValue enumFromJSON) {
    values.add(enumFromJSON);
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    EnumDefinitionValue edv = new EnumDefinitionValue(type, new ArrayList<EnumValue>());
    for (EnumValue ev : values) edv.addEnumValue((EnumValue) ev.clone());
    return edv;
  }

  @Override
  public boolean equals(Object obj) {
    if(obj == null || !(obj instanceof EnumDefinitionValue))
      return false;
    EnumDefinitionValue lv = (EnumDefinitionValue)obj;
    if(!lv.type.equals(type))
      return false;
    return (values == null && lv.values == null || values != null&& lv.values!=null &&
        Arrays.equals(values.toArray(),(lv.values.toArray())));
  }
}
