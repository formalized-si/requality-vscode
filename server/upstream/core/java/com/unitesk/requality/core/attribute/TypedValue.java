package com.unitesk.requality.core.attribute;

public class TypedValue {
  private final AttributeType at;
  private final Object value;
  public TypedValue(AttributeType at, Object val){
    this.at = at;
    this.value = val;
  }
  public Object getValue() {
    return value;
  }
  public AttributeType getType(){
    return at;
  }
  public <T> T getGValue() {
    return (T)value;
  }
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    sb.append(getType());
    sb.append("]");
    sb.append(getValue()!=null?getValue().toString():"null");
    sb.append("{");
    sb.append(super.toString());
    sb.append("}");
    return sb.toString();
  }
  @Override
  public boolean equals(Object obj) {
    if(obj == null)
      return false;
    if(!(obj instanceof TypedValue))
      return false;
    TypedValue tv = (TypedValue) obj;
    if(!tv.getType().equals(getType()))
      return false;
    return Attribute.equalsValues(tv.getValue(), getValue());
  }
}
