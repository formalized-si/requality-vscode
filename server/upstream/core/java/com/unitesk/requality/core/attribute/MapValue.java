package com.unitesk.requality.core.attribute;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class MapValue implements /*Iterable<Object>,*/ Cloneable,IContextStringProvider {
  private AttributeType type;
  private Map<String, Object> values;

  public MapValue(AttributeType atype, Map<String, Object> values) {
    this.type = atype;
    this.values = values;
  }

  public AttributeType getType() {
    return type;
  }
//  public Set<Entry<String, Object>> iterator(){
//    return values.entrySet(); 
//  }
//
//  public <T> Iterator<T> giterator(){
//    return new Iterator<T>(){
//      Iterator<Object> it = values.iterator();
//      @Override
//      public boolean hasNext() {
//        return it.hasNext();
//      }
//
//      @Override
//      public T next() {
//        return (T)it.next();
//      }
//    };
//  }

  public Map<String,Object> getValues() {
    return values;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for(Entry<String, Object> o :getValues().entrySet()){
      if(sb.length()>1)
        sb.append(", ");
      if(o!=null){
    	sb.append(o.getKey());
    	sb.append("=");
    	if(o.getValue()!=null)
    		sb.append(o.getValue().toString());
    	else
    		sb.append("NULL");
      }
    }
    sb.append("]");
    return sb.toString();
  }
  @Override
  public String toString(AttributeStorage as) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for(Entry<String, Object> o :getValues().entrySet()){
      if(sb.length()>1)
        sb.append(", ");
      if(o!=null){
      	sb.append(o.getKey());
      	sb.append("=");
      	if(o.getValue()!=null){
      		Object oa = o.getValue();
            if(oa instanceof IContextStringProvider)
              sb.append(((IContextStringProvider)oa).toString(as));
            else
              sb.append(oa.toString());
      	}else
      		sb.append("NULL");
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
    Map<String, Object> lst = new HashMap<>();
    for (Entry<String, Object> o : values.entrySet()) {
      if (type == AttributeType.ENUM) lst.put(o.getKey(),((AppliedEnumValue) o.getValue()).clone());
      else if (type == AttributeType.LIST) lst.put(o.getKey(),((ListValue) o.getValue()).clone());
      else if (type == AttributeType.MAP) lst.put(o.getKey(),((MapValue) o.getValue()).clone());
      else if (type == AttributeType.ENUM_DEFINITION) lst.put(o.getKey(),((EnumDefinitionValue) o.getValue()).clone());
      else lst.put(o.getKey(),o.getValue());
    }
    return new MapValue(type, values);
  }
  @Override
  public boolean equals(Object obj) {
    if(obj == null || !(obj instanceof MapValue))
      return false;
    MapValue lv = (MapValue)obj;
    if(!lv.type.equals(type))
      return false;
    return (values == null && lv.values == null || values != null&& lv.values!=null &&
        Arrays.equals(values.keySet().toArray(),(lv.values.keySet().toArray()))&&
        Arrays.equals(values.values().toArray(),lv.values.values().toArray()));
  }


}
