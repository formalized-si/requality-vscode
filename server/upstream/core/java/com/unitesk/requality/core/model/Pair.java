package com.unitesk.requality.core.model;

public class Pair<T, Z> implements Comparable<T> {
  private T o1;
  private Z o2;

  public static Pair<String, String> createSS(String o1, String o2) {
    return new Pair<String, String>(o1, o2);
  }
  public static Pair<String, Object> createSO(String o1, Object o2) {
	return new Pair<String, Object>(o1, o2);
  }
  
  public Pair(T o1, Z o2) {
    this.o1 = o1;
    this.o2 = o2;
  }

  public T getFirst() {
    return o1;
  }

  public Z getSecond() {
    return o2;
  }
  @Override
  public boolean equals(Object obj) {
    if(obj == null)
      return false;
    if(!(obj instanceof Pair))
      return false;
    Pair<T, Z> p2 = (Pair<T, Z>) obj;
    return (o1 == null && p2.o1 == null || o1!=null && o1.equals(p2.o1)) &&
        (o2 == null && p2.o2 == null || o2!=null && o2.equals(p2.o2));
  }

  @Override
  public int compareTo(T obj) {
    if(obj == null)
      return 1;
    if(!(obj instanceof Pair))
      return 1;
    
    return ((Comparable<T>) o1).compareTo(((Pair<T, Z>)obj).o1);
  }
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("[");
    if(o1!=null)
      sb.append(o1.toString());
    else
      sb.append("null");
    sb.append(",");
    if(o2!=null)
      sb.append(o2.toString());
    else
      sb.append("null");
    sb.append("]");
    return sb.toString();
  }
}
