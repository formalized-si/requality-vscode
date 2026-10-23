package com.unitesk.requality.core.attribute;

public class AppliedEnumValue extends EnumValue implements Cloneable, IContextStringProvider {
  private AttributeType at;
  private String basekey;

  public AppliedEnumValue(Object value, String comment, AttributeType at, String baseKey) {
    super(value, comment);
    this.at = at;
    this.basekey = baseKey;
  }

  public AttributeType getType() {
    return at;
  }

  public String getBaseKey() {
    return basekey;
  }

  public void updateCommentValue(String string, String comm) {
    this.val = string;
    this.comm = comm;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return new AppliedEnumValue(val, comm, at, basekey);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (!(obj instanceof AppliedEnumValue)) return false;
    AppliedEnumValue aev = (AppliedEnumValue) obj;
    if (aev.at == at && (aev.getValue()==null&&getValue()==null || aev.getValue()!=null && getValue()!=null && aev.getValue().equals(getValue()))) {
      String c1 = aev.getComment(), c2 = getComment();
      if (c1 == null && c2 == null) return true;
      else if (c1 == null || c2 == null) return false;
      else return c1.equals(c2);
    }
    return super.equals(obj);
  }
  @Override
  public String toString() {
    return Attribute.valueToString(null, getType(), getValue());
  }

  @Override
  public String toString(AttributeStorage as) {
    return Attribute.valueToString(as, getType(), getValue());
  }
}
