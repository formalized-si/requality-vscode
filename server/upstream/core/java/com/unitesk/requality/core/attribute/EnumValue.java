package com.unitesk.requality.core.attribute;

public class EnumValue implements Cloneable {
  protected Object val;
  protected String comm;

  public EnumValue(Object value, String comment) {
    this.val = value;
    this.comm = comment;
  }

  public Object getValue() {
    return val;
  }

  public String getComment() {
    return comm;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof EnumValue))
      return obj == null && val == null || obj != null && obj.equals(val);
    EnumValue ev = (EnumValue) obj;
    return ev.val == null && val == null || ev.val != null && ev.val.equals(val);
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return new EnumValue(val, comm);
  }

  public void setValue(Object string) {
    val = string;
  }

  public void setComment(String c) {
    comm = c;
  }

  public AppliedEnumValue createApplied(AttributeType at, String enumName) {
    return new AppliedEnumValue(val, comm, at, enumName);
  }
}
