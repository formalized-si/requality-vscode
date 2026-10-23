package com.unitesk.requality.vscode.port.eclipse.core;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.jsonclone.LoadedJSONObject;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.NodeMapper;
import com.unitesk.requality.tool.CoreUtils;

public class ReqSettings {
  private String sname = null;
  private Map<String,Object> values;
  public ReqSettings(String canonicalName) {
    sname = canonicalName;
    values = new HashMap<>();
  }

  public void load(String canonicalName, TreeDB db) throws IOException, JSONException {
    java.io.File f = CoreUtils.getTempDir().resolve(canonicalName).toFile();
    if(f.exists()){
      f.deleteOnExit();
      String source = Files.readAllLines(f.toPath(), Charset.forName("UTF-8")).
          stream().collect(Collectors.joining("\n"));
      try(LoadedJSONObject obj = new LoadedJSONObject(source)){
        Iterator<Object> ki = obj.keys();
        while(ki.hasNext()){
          Object o = ki.next();
          if(o != null){
            String k = o.toString();
            JSONObject ov = obj.getJSONObject(k);
            String tp = ov.getString("type");
            switch(tp){
              case "attribute":{
                values.put(k, NodeMapper.loadValue(ov));
              }break;
              case "node":{
                String id = ov.getString("value");
                TreeNode node = db.getNode(UUID.fromString(id));
                values.put(k, node);
              }break;
              
            }
          }
        }
      }
    }
  }
  public void save(String canonicalName) throws IOException, JSONException {
    java.io.File f = CoreUtils.getTempDir().resolve(canonicalName).toFile();
    if(!f.exists())
      f.getParentFile().mkdirs();
    f.createNewFile();
    f.deleteOnExit();
    JSONObject jo = new JSONObject();
    for(String s:values.keySet()){
      Object o = values.get(s);
      JSONObject jo2 = new JSONObject();
      String tp = "attribute";
      if(o instanceof TreeNode){
        tp = "node";
        o = ((TreeNode)o).getSUUId();
      }else{
        if(o instanceof TypedValue)
          NodeMapper.saveValue(jo2, (TypedValue)o);
        else
          NodeMapper.saveValue(jo2, new TypedValue(AttributeType.STRING, o.toString()));
      }
      jo2.put("type", tp);
      jo.put(s, jo2);
    }
    Files.write(f.toPath(), jo.toString(4).getBytes(Charset.forName("UTF-8")));
  }
  public void put(String string, Object val) {
    if(!(val instanceof TreeNode) && !(val instanceof TypedValue))
      val = new TypedValue(AttributeType.STRING, val!=null?val.toString():null);
    values.put(string, val);
  }

  public String getSectionName() {
    return sname;
  }

  public Set<String> getKeys() {
    return values.keySet();
  }

  public Object get(String s) {
    return values.get(s);
  }

  public Map<String, Object> getMap() {
    if(values == null)
      values = Collections.emptyMap();
    return values;
  }

  public void setMap(Map<String, Object> sw) {
    if(values!=null)
      values.clear();
    else
      values = new HashMap<>();
    for(String s:sw.keySet())
      put(s, sw.get(s));
  }

  public String getString(String levelPattern) {
    TypedValue tv = (TypedValue) values.get(levelPattern);
    if(tv!=null)
      return tv.getGValue();
    return null;
  }
}
