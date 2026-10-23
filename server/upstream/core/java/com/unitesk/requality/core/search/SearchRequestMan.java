package com.unitesk.requality.core.search;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;

public class SearchRequestMan {
  public final static String SMNAME = "|sm|";
  public static ISearchRequest restore(Map<String, String> args) {
    SearchMethod sm = loadMethod(args);
    Map<String, TypedValue> params = new HashMap<>();
    for(String s:args.keySet()){
      if(!s.equals(SMNAME))
        params.put(s, new TypedValue(AttributeType.STRING, args.get(s)));
      //TODO for typed vals can be two vals or two maps
    }
    if(sm == SearchMethod.BY_ATTRIBUTE)
      return new AttributeRequest(params);

    if(sm == SearchMethod.BY_PATH)
      return new RequestByPath(params);
    if(sm == SearchMethod.BY_REQUEST)
      return new SearchByRequest(params);
  return null;//TODO or, and or not
    
  }
  public static SearchMethod loadMethod(Map<String, String> args) {
  return SearchMethod.valueOf(args.getOrDefault(SMNAME, SearchMethod.BY_PATH.name()));
  }

  public static Map<String, TypedValue> fromStored(SearchMethod sm, Map<String, String> params) {
    Map<String, TypedValue> saved = new HashMap<>();
    //TODO add or,not,and
    if(sm!=null)
      saved.put(SMNAME, new TypedValue(AttributeType.STRING,sm.name()));
    for(Entry<String, String> e:params.entrySet()){
      String tv = e.getValue();
      if(tv!=null)
      {
        saved.put(e.getKey(), new TypedValue(AttributeType.STRING,e.getValue()));
      }
    }
    return saved;
  }
  public static Map<String, String> toStored(SearchMethod sm, 
      Map<String,TypedValue> params){
    Map<String, String> saved = new HashMap<>();
    //TODO add or,not,and
    if(sm!=null)
      saved.put(SMNAME, sm.name());
    for(String s:params.keySet()){
      TypedValue tv = params.get(s);
      if(tv!=null)
      {
        if(tv.getType()==AttributeType.STRING){
          saved.put(s, tv.getGValue());
        }else{
          //TODO untested
          throw new RuntimeException("non string params cannot be saved for now");
        }
      }
    }
    return saved;
  }
  public static SearchMethod getMethod(ISearchRequest req) {
    if(req instanceof AttributeRequest)
      return SearchMethod.BY_ATTRIBUTE;
    if(req instanceof SearchByRequest)
      return SearchMethod.BY_REQUEST;
    if(req instanceof RequestByPath)
      return SearchMethod.BY_PATH;
    return null;
  }
}
