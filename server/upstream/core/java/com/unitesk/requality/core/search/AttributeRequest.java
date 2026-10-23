package com.unitesk.requality.core.search;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.SystemNames;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.CoreUtils;

public class AttributeRequest implements ISearchRequest {

  private Map<String,TypedValue> params;
  public static String USER_VISIBLE_KEY="_*uv*_";
  public static AttributeRequest fromString(Pair<String,String>... sparams){
    Map<String, TypedValue> ret = new HashMap<>();
    for(Pair<String, String> s: sparams)
      ret.put(s.getFirst(), new TypedValue(AttributeType.STRING,
          s.getSecond()));
    return new AttributeRequest(ret);
  }
  
  public AttributeRequest(Map<String,TypedValue> params){
    this.params = params;
  }
  public AttributeRequest(String key, AttributeOperator op, String value, boolean caseSensitive) {
	this(toMap(key,op,value, caseSensitive));
  }
  private static Map<String, TypedValue> toMap(String key, AttributeOperator op, String value, boolean caseSensitive) {
	Map<String, TypedValue> ret = new HashMap<>();
	ret.put("key",new TypedValue(AttributeType.STRING,key));
	ret.put("op",new TypedValue(AttributeType.STRING,op.name()));
    ret.put("sample",new TypedValue(AttributeType.STRING,value));
    ret.put("cs", new TypedValue(AttributeType.STRING,caseSensitive));
    return ret;
 }
  private String key = null,ra = null;
  private AttributeOperator op = null;
  private boolean cs = false;
  
  @Override
  public boolean init(TreeNode node) {
    key = params.get("key").getGValue();
    op = AttributeOperator.valueOf(params.get("op").getGValue());
    Object toc = params.get("sample");
    if(toc!=null)
      toc = ((TypedValue)toc).getValue();
    ra = "";
    if(toc!=null)
      ra = toc.toString();
    toc = params.get("cs");
    cs = false;
    if(toc!=null)
    	cs = Boolean.parseBoolean(((TypedValue)toc).getValue().toString());
    if(!cs)
    	ra = ra.toLowerCase();
    return true;
  }

  @Override
  public boolean test(TreeNode node) {
    if(key.equals(USER_VISIBLE_KEY)) {
      for(Entry<String, Attribute> e:node.attrs().getActiveAttributes().entrySet()) {
        String akey = e.getKey();
        Attribute a = e.getValue();
    
//      if(op == AttributeOperator.NOT_EXISTS && 
//            (a == null || a.getValue() == null))
//          return true;
//        if(op == AttributeOperator.EXISTS && 
//            (a != null && a.getValue() != null))
//          return true;
        if(a == null || a.getValue() == null || !a.isUserVisible() && SystemNames.getOne(node.getTreeDB()).getVisSysName(e.getKey())==null)
          continue;//check only exist
        String aval = a.getValue().toString();
        if(!cs) 
        	aval = aval.toLowerCase();
        if(op == AttributeOperator.EQUALS)
          if(ra.equals(aval))
            return true;
        if(op == AttributeOperator.NOT_EQUALS&&!ra.equals(aval))
          return true;
        if(op == AttributeOperator.CONTAINS_SINGLE_SPACE && 
        		(aval.contains(ra)||
        		  CoreUtils.filterOutText(aval,false).contains(ra)||
        		  CoreUtils.filterOutText(aval,true).contains(CoreUtils.spaceOne(ra))))
          return true;
        if(op == AttributeOperator.CONTAINS && aval.contains(ra))
          return true;
        if(op == AttributeOperator.NOT_CONTAINS&&!aval.contains(ra))//TODO mb use contains logic
          return true;
      }
      return false;
    }else {
      Attribute a = node.attrs().getAttribute(key);
      if(op == AttributeOperator.NOT_EXISTS && 
          (a == null || a.getValue() == null))
        return true;
      if(op == AttributeOperator.EXISTS && 
          (a != null && a.getValue() != null))
        return true;
      if(a == null || a.getValue() == null)
        return false;//check only exist
      String aval = a.getValue().toString();
      if(!cs) 
      	aval = aval.toLowerCase();
      if(op == AttributeOperator.EQUALS)
        return ra.equals(aval);
      if(op == AttributeOperator.NOT_EQUALS)
        return !ra.equals(aval);
      if(op == AttributeOperator.CONTAINS)
        return aval.contains(ra);
      if(op == AttributeOperator.CONTAINS_SINGLE_SPACE)
        return aval.contains(ra)||
        		CoreUtils.filterOutText(aval,false).contains(ra)||
        		CoreUtils.filterOutText(aval,true).contains(CoreUtils.spaceOne(ra));
      if(op == AttributeOperator.NOT_CONTAINS)
        return !aval.contains(ra);
    }
    return false;
  }

  @Override
  public boolean actionAndIgore(TreeNode root, Set<TreeNode> matches) {
    return false;
  }
  public String get(String string) {
    TypedValue tv = params.get(string);
    return tv == null ? null : tv.getGValue();
  }
  public void put(String name, TypedValue val){
    if(val == null)
      params.remove(name);
    else
      params.put(name, val);
  }
  

}
