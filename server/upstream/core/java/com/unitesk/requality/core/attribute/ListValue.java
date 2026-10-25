package com.unitesk.requality.core.attribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ListValue implements Iterable<Object>, Cloneable,IContextStringProvider {
  private AttributeType type;
  private List<Object> values;

  public ListValue(AttributeType atype, List<Object> values) {
    this.type = atype;
    this.values = values;
  }

  public AttributeType getType() {
    return type!=null?type:AttributeType.STRING;//adds default
  }
  public Iterator<Object> iterator(){
    return values.iterator(); 
  }

  public <T> Iterator<T> giterator(){
    return new Iterator<T>(){
      Iterator<Object> it = values.iterator();
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public T next() {
        return (T)it.next();
      }
    };
  }

  public List<Object> getValues() {
    return values;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for(Object o :getValues()){
      if(sb.length()>1)
        sb.append(", ");
      if(o!=null){
        sb.append(o.toString());
      }
    }
    sb.append("]");
    return sb.toString();
  }
  @Override
  public String toString(AttributeStorage as) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for(Object o :getValues()){
      if(sb.length()>1)
        sb.append(", ");
      if(o!=null){
        if(o instanceof IContextStringProvider)
          sb.append(((IContextStringProvider)o).toString(as));
        else
          sb.append(o.toString());
      }
    }
    sb.append("]");
    return sb.toString();
  }
  
//  @Override
//  public Iterator<Object> iterator() {
//    return new Iterator<Object>() {
//      int ind = 0;
//
//      @Override
//      public void remove() {
//        values.remove(ind);
//      }
//
//      @Override
//      public Object next() {
//        return values.get(ind++);
//      }
//
//      @Override
//      public boolean hasNext() {
//        return ind < values.size();
//      }
//    };
//  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    List<Object> lst = new ArrayList<>();
    //TODO references?
    for (Object o : values) {
      if (type == AttributeType.ENUM) lst.add(((AppliedEnumValue) o).clone());
      else if (type == AttributeType.LIST) lst.add(((ListValue) o).clone());
      else if (type == AttributeType.MAP) lst.add(((MapValue) o).clone());
      else if (type == AttributeType.ENUM_DEFINITION) lst.add(((EnumDefinitionValue) o).clone());
      else lst.add(o);
    }
    return new ListValue(type, lst);
  }
  @Override
  public boolean equals(Object obj) {
    if(obj == null || !(obj instanceof ListValue))
      return false;
    ListValue lv = (ListValue)obj;
    if(!lv.type.equals(type))
      return false;
    return (values == null && lv.values == null || values != null&& lv.values!=null &&
        Arrays.equals(values.toArray(),(lv.values.toArray())));
  }


}
