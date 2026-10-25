package com.unitesk.requality.core.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
  public void test(TreeNode node, List<String> attrFound) {
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
        if(!filteredUserVisible(node, e.getKey(), a))
          continue;//check only exist
        String aval = a.getValue().toString();
        if(!cs) 
          aval = aval.toLowerCase();
        if(op == AttributeOperator.EQUALS)
          if(ra.equals(aval))
            attrFound.add(akey);
        if(op == AttributeOperator.NOT_EQUALS&&!ra.equals(aval))
          attrFound.add(akey);
        if(op == AttributeOperator.CONTAINS_SINGLE_SPACE && 
        		(aval.contains(ra)||
        		  CoreUtils.filterOutText(aval,false).contains(ra)||
        		  CoreUtils.filterOutText(aval,true).contains(CoreUtils.spaceOne(ra))))
          attrFound.add(akey);
        if(op == AttributeOperator.CONTAINS && aval.contains(ra))
          attrFound.add(akey);
        if(op == AttributeOperator.NOT_CONTAINS&&!aval.contains(ra))//TODO mb use contains logic
          attrFound.add(akey);
      }
    }else {
      Attribute a = node.attrs().getAttribute(key);
      if(op == AttributeOperator.NOT_EXISTS && 
          (a == null || a.getValue() == null))
        attrFound.add(key);
      if(op == AttributeOperator.EXISTS && 
          (a != null && a.getValue() != null))
        attrFound.add(key);
      String aval = null;
      if(a == null || a.getValue() == null){
        aval = "";
      }else
        aval = a.getValue().toString();
      if(!cs) 
      	aval = aval.toLowerCase();
      if(op == AttributeOperator.EQUALS)
        if(ra.equals(aval))
          attrFound.add(key);
      if(op == AttributeOperator.NOT_EQUALS)
        if(!ra.equals(aval))
          attrFound.add(key);
      if(op == AttributeOperator.CONTAINS)
        if(aval.contains(ra))
          attrFound.add(key);
      if(op == AttributeOperator.CONTAINS_SINGLE_SPACE)
        if(aval.contains(ra)||
        		CoreUtils.filterOutText(aval,false).contains(ra)||
        		CoreUtils.filterOutText(aval,true).contains(CoreUtils.spaceOne(ra)))
          attrFound.add(key);
      if(op == AttributeOperator.NOT_CONTAINS)
        if(!aval.contains(ra))
          attrFound.add(key);
    }
  }

  public static boolean filteredUserVisible(TreeNode node, String key2,Attribute a){// TODO Auto-generated method stub
     return a != null && a.getValue() != null && (a.isUserVisible() || SystemNames.getOne(node.getTreeDB()).getVisSysName(key2)!=null);
  }
 
  @Override
  public boolean actionAndIgore(TreeNode root, List<Pair<? extends TreeNode, List<String>>> matches) {
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
