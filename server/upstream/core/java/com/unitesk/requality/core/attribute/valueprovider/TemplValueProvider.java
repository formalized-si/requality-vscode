package com.unitesk.requality.core.attribute.valueprovider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.jsonclone.JSONArray;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.IContextStringProvider;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.NodeMapper;

public class TemplValueProvider implements IValueProvider{
  TemplValueProvider(){
    
  }
  public TemplValueProvider(TypedValue tv, Set<String> deps){ 
    init(tv.getType(),tv.getValue(), deps);
  }
  private static Set<String> toSet(List<Object> values) {
    Set<String> ret = new HashSet<>();
    for(Object o:values)
      ret.add(o.toString());
    return ret;
  }
  protected AttributeType at;//Tends to be always ~= STRING OR List for now
  protected Object val;
  protected Set<String> deps;
  public static String TYPE="TemplateValueProvider";
  
  public TemplValueProvider(AttributeType at, Object newVal){
    init(at,newVal,null);
  }

  private void init(AttributeType at2, String val2) {
    init(at2,val2,null);
  }
  protected void init(AttributeType at2, Object val2, Set<String> dps) {
    this.at = at2;
    this.val = val2;
    if(val2 == null){
      deps = NULLDEP;
      return;
    }
    if(dps!=null)
      deps = dps;
    else{
      deps = new HashSet<>();
      stringDepsLookup(deps, val2.toString());
    }
  }

  public TemplValueProvider(ListValue vals){
    init(vals);
  }
  
  private void init(ListValue vals) {
    this.at = AttributeType.LIST;
    this.val = vals;
    if(vals == null){
      deps = NULLDEP;
      return;
    }
    deps = new HashSet<>();
    recDepsLookup(deps, vals);
  }

  public TemplValueProvider(AttributeType at, Object nv, Set<String>deps) {
    if(nv instanceof ListValue)
      init((ListValue)nv);
    else
      init(at, nv!=null?nv.toString():null, deps);
  }

  private void recDepsLookup(Set<String> deps, ListValue vals) {
    if(vals.getType()==AttributeType.LIST){
      for(Object o:vals.getValues()){
        ListValue lv = (ListValue)o;
        recDepsLookup(deps, lv);
      }
    }else
    {
      if(vals.getType()==AttributeType.STRING || vals.getType()==AttributeType.HTML
          || vals.getType()==AttributeType.REFERENCE){
        for(Object o:vals.getValues()){
          String one = o.toString();
          stringDepsLookup(deps, one);
        }
      }
    }
  }
  private static void stringDepsLookup(Set<String> names, String val) {
    int o = val.indexOf("{"), e;
    if(o==-1)
      return;
    int last = 0;
    while (o > -1) {
      e = val.indexOf("}", o);
      if (e > -1) {
        String name = val.substring(o + 1, e);
        if (Attribute.checkNameIsValid(name,true)){ 
          names.add(name);
        }
      } else e = o;
      o = val.indexOf("{", e + 1);
    }
  }


  private static Object calculateStringValue(AttributeStorage as, String val, Map<String,String> map) {
    String strval = val;
    StringBuilder sb = new StringBuilder();
    //TODO use BracesHanlder
    int o = strval.indexOf("{"), e;
  if(o==-1)
    return val;
  int last = 0;
  while (o > -1) {
    e = strval.indexOf("}", o);
      if (e > -1) {
        String name = strval.substring(o + 1, e);
        if (Attribute.checkNameIsValid(name,true)){
          if(as!=null){
            String repl = map.get(name);
            Attribute a = as.getAttribute(name);
            if(a!=null&&a.getTValue()!=null && a.getTValue().getValue()!=null){
              Object ov = a.getTValue().getValue();
              if(ov instanceof IContextStringProvider)
                repl = ((IContextStringProvider)ov).toString(as);
              else
                repl = ov.toString();
            }
            if(repl!=null)
            {
              sb.append(strval.substring(last,o));
              sb.append(repl);
            }else
              sb.append(strval.substring(last,e+1));
            last = e+1;
          }
        }
      } else e = o;
      o = strval.indexOf("{", e+1);
    }
    sb.append(strval.substring(last,strval.length()));
    return sb.toString();
  }


  private static ListValue calculateListValue(AttributeStorage as, ListValue vals,
      Map<String,String> map) {
    ArrayList<Object> hndls = new ArrayList<>();
    boolean changed = false;
    for(Object o:vals.getValues()){
      Object res = calculateValue(as, vals.getType(), o, map);
      hndls.add(res);
      if(res!=o)
        changed = true;
    }
    if(changed)
      return new ListValue(vals.getType(), hndls);
    return vals;
  }
  
  protected static Object calculateValue(AttributeStorage as,  AttributeType atype, Object
val, Map<String,String> map) {
    if(atype.equals(AttributeType.LIST))
      return calculateListValue(as, (ListValue)val, map);
    if(atype==AttributeType.STRING  ||
       atype==AttributeType.HTML)
      return calculateStringValue(as, (String)val, map);
    return val;
  }
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    return new TypedValue(at, calculateValue(as, at, val, Collections.emptyMap()));
  }
  
//  @Override
//  public TypedValue getTypedValue(String key, AttributeStorage as) {
//    return new TypedValue(at, replaceValue(as,Collections.emptyMap()));
//  }

 
  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return deps;
  }
//  @Override
//  public IValueProvider clone(){ 
//    if(islist)
//      return new TemplValueProvider((ListValue)val);
//    return new TemplValueProvider(at,val.toString());
//  }

  @Override
  public boolean equals(Object obj) {
    if(!(obj instanceof TemplValueProvider))
      return false;
    TemplValueProvider cvp = (TemplValueProvider)obj;
    if(!at.equals(cvp.at))
      return false;
    return Attribute.equalsValues(val, cvp.val);
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public TypedValue getRawValue() {
    return new TypedValue(at, val);
  }

  @Override
  public Map<String, Object> getRawStores() {
    Map<String,Object> sett = new HashMap<>(1);
    sett.put("deps", new ListValue(AttributeType.STRING, new ArrayList<Object>(deps)));
    return sett;
  }
  @Override
  public void attach(TreeNode node) {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void detach(TreeNode node) {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void saveJSON(JSONObject vpo) throws JSONException {
    NodeMapper.saveValue(vpo, at, val);
    if(deps != null&&!deps.isEmpty())
      vpo.put("deps", new JSONArray(deps.toArray()));
  }
  @Override
  public AttributeType getAttributeType() {
    return at;
  }
}
