package com.unitesk.requality.core.attribute;

import java.util.Random;

public class Version implements Comparable<Version> {
  private Integer version;

  public static Version getUnset() {
    return new Version(null);
  }

  public Version() {
    this(null);
  }

  public Version(Integer v) {
    if (v == null) version = null;
    else version = v.intValue();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Version) {
      Integer l1 = version, l2 = ((Version) obj).version;
      if (l1 == null || l2 == null) return l1 == null && l2 == null;
      return l1.equals(l2);
    }
    return false;
  };

  @Override
  public int compareTo(Version o) {
    if (o.version == null) return version == null ? 0 : -1;
    if (version == null) return o.version == null ? 0 : 1;
    return Long.compare(version, o.version);
  }

  public Integer getInt() {
    if (version == null) return version;
    return version.intValue();
  }

  public long getSInt() {
    if (version == null) return -2L;
    return version;
  }

  static Random r = new Random();

  public static Version getZero() {
    return new Version(0);
  }

  public static Version getMinusOne() {
    return new Version(Integer.MIN_VALUE);
  }

  public static Version fromString(String s) {
    if (s.equals("null") || s.equals("-2")) return getUnset();
    try {
      return new Version(Integer.parseInt(s));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return getUnset();
  }

  @Override
  public String toString() {
    return version == null ? "null" : version.toString();
  }

  public boolean isUnset() {
    return version == null;
  }

  public void inc() {
    if (isUnset()) throw new RuntimeException("Call for increment of unset version");
    version++;
  }

  @Override
  public Version clone() {
    if(version!=null)
      return new Version(new Integer(version));
    return getUnset(); 
  }

  public void setInt(Integer v) {
    if (v == null && this == Version.getUnset())
      throw new RuntimeException("Attempt to set undefined value");
    if (v == null) version = null;
    else version = new Integer(v);
  }
}
