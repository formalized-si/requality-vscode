package com.unitesk.requality.core.attribute.valueprovider;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.dslplatform.json.JsonReader;
import com.unitesk.jsonclone.JSONArray;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.storage.NodeMapper;

public class VPFactory {
  private static Map<String,IVPCreator> constrs = new HashMap<>();
  private static VPFactory ft = new VPFactory();

  static{
    constrs.put(ConstValueProvider.TYPE, new IVPCreator(){
      @Override
      public IValueProvider create(JSONObject object, IRelatedFilesProvider resFolder) throws JSONException {
        return new ConstValueProvider(NodeMapper.loadValue(object));
      }
      @Override
      public IValueProvider create(
          UUID uid,
          JsonReader reader,
          AttributeType atype,
          AttributeType etype,
          String enumKey,
          IRelatedFilesProvider resFolder,
          Map<String, Object> addParams)
          throws IOException {
        return new ConstValueProvider(new TypedValue(atype,
            NodeMapper.readTPValue(uid, atype, etype, enumKey, addParams, reader)));
      }
      
    });
    constrs.put(ReferenceValueProvider.TYPE, new IVPCreator(){
      @Override
      public IValueProvider create(JSONObject object, IRelatedFilesProvider resFolder) throws JSONException {
        //old format loading
        String atype = object.optString("atype", "REFERENCE");
        int cnt = 0;
        Iterator<Object> it = object.keys();
        while(it.hasNext()) {
          it.next();cnt++;
        }
        if(atype.equals("LIST")) {
          String ltype = object.optString("elements_type", "REFERENCE");
          if(cnt>4)
            RLogger.logDebug(object.toString(),5);
          if(!ltype.equals("REFERENCE"))
            throw new RuntimeException("incorrect list of refs on "+object);
          List<ReferenceRawValue> lrefs = new LinkedList<>();
          try {
            JSONArray la = object.getJSONArray("value");
            if(la!=null) {
              for(int i = 0;i<la.length();i++) {
                JSONObject objval = la.optJSONObject(i);
                if(objval == null) {
                  String sa = la.optString(i,"");
                  if(sa!=null)
                    lrefs.add(new ReferenceRawValue(sa,null,null));
                }else {
                  String node = objval.optString("node");
                  String value = objval.getString("value");
                  String vers = objval.optString("version");
                  lrefs.add(new ReferenceRawValue(value,node,vers));
                }
              }
            }
          }catch(JSONException je) {
            RLogger.logDebug("value is not found for "+object.toString());
          }
          return new ReferenceValueProvider(lrefs);
        }else
        {
          if(cnt>3)
            RLogger.logDebug(object.toString(),5);
          JSONObject objval = object.optJSONObject("value");
          if(objval == null) {
            String value = object.getString("value");
            return new ReferenceValueProvider(AttributeType.REFERENCE, value);
          }else {
            String node = objval.optString("node");
            String value = objval.getString("value");
            String vers = objval.optString("version");
            return new ReferenceValueProvider(value,node,vers);
          }
        }
      }
      @Override
      public IValueProvider create(
          UUID uid, 
          JsonReader reader,
          AttributeType atype,
          AttributeType etype,
          String enumKey,
          IRelatedFilesProvider resFolder,
          Map<String, Object> addParams)
          throws IOException {
        return new ReferenceValueProvider(atype, etype, reader);
//        Map<UUID, String> vers = null; 
//        if(atype == AttributeType.REFERENCE){
//        //  if(reader.getLastName() == "storedVersions"){
//            //TODO read 
//          //}
//        }
//        
//        return new ReferenceValueProvider(new TypedValue(atype,
//            NodeMapper.readTPValue(atype, etype, enumKey, addParams, reader)));
      }
      
    });
    constrs.put(TemplValueProvider.TYPE, new IVPCreator(){
      @Override
      public IValueProvider create(JSONObject object, IRelatedFilesProvider resFolder) throws JSONException {
        Set<String> deps = new HashSet<>();
        if(object.has("deps")){
          JSONArray ar = object.getJSONArray("deps");
          for(int i = 0;i<ar.length();i++)
            deps.add(ar.getString(i));
        }
        TypedValue tv = NodeMapper.loadValue(object);
        return new TemplValueProvider(tv,deps);
      }

      @Override
      public IValueProvider create(
          UUID uid, 
          JsonReader reader,
          AttributeType atype,
          AttributeType etype,
          String enumKey,
          IRelatedFilesProvider resFolder,
          Map<String, Object> addParams)
          throws IOException {
        Set<String> deps = Collections.emptySet();
        if(addParams!=null){
          Object d = addParams.get("deps");
          if(d!=null){
            deps = new HashSet<>();
            List<String>depsa = (List<String>) d;
            for(String s:depsa)
              deps.add(s);
          }
        }
        return new TemplValueProvider(
            new TypedValue(atype, NodeMapper.readTPValue(uid, atype, etype, enumKey, addParams, reader)),
            deps);
      }
    });

    constrs.put(HtmlValueProvider.TYPE,new IVPCreator(){
      @Override
      public IValueProvider create(JSONObject object, IRelatedFilesProvider resFolder) throws JSONException {
        Set<String> deps = new HashSet<>();
        if(object.has("deps")){
          JSONArray ar = object.getJSONArray("deps");
          for(int i = 0;i<ar.length();i++)
            deps.add(ar.getString(i));
        }
        TypedValue tv = NodeMapper.loadValue(object);
        return new HtmlValueProvider(tv.getType(),
            tv.getValue(),
            deps,
            resFolder);//TODO res provider
      }
      

      @Override
      public IValueProvider create(
          UUID uid, 
          JsonReader reader,
          AttributeType atype, AttributeType etype, String enumKey,
          IRelatedFilesProvider resFolder,
          Map<String, Object> addp)
          throws IOException {
        Set<String> deps = Collections.emptySet();
        if(addp!=null){
          Object d = addp.get("deps");
          if(d!=null){
            deps = new HashSet<>();
            List<Object>depsa = (List<Object>) d;
            for(Object s:depsa)
              deps.add((String)s);
          }
        }
        return new HtmlValueProvider(atype,
            NodeMapper.readTPValue(uid, atype, etype, enumKey, addp, reader),
            deps,
            resFolder);//TODO res provider
      }
    });
    constrs.put(GeneratorValueProvider.TYPE, new IVPCreator(){
      @Override
      public IValueProvider create(JSONObject object, IRelatedFilesProvider resFolder) throws JSONException {
        UUID uid = UUID.fromString(object.getString("uuid"));
        Set<String> keys = new HashSet<>();
        Map<String,TypedValue>setts = new HashMap<>();
        Iterator<Object> it = object.keys();
        while(it.hasNext()){
          Object o = it.next();
          if(o!=null){
            JSONObject jo = object.optJSONObject(o.toString());
            if(jo!=null)
              setts.put(o.toString(), NodeMapper.loadValue(jo));
            else
              setts.put(o.toString(), Attribute.getTypedValue(object.getString(o.toString())));
          }
        }
        
        return new GeneratorValueProvider(uid, setts);
      }

      @Override
      public IValueProvider create(UUID uid, 
          JsonReader reader,
          AttributeType atype, AttributeType etype, String enumKey,
          IRelatedFilesProvider resFolder,
          Map<String, Object> addp)
          throws IOException {
//        if(reader.last() != '{')
//          throw new IOException("wrong vp format");
//        if(reader.getNextToken() == '}')
//          throw new IOException("Empty generator provider");
        Map<String, TypedValue> vals = new TreeMap<>();
        byte deep = 1;
        for(String s:addp.keySet()){
          Object v = addp.get(s);
          if(v instanceof TypedValue)
            vals.put(s, (TypedValue) v);
          else
            vals.put(s, new TypedValue(AttributeType.detectType(v), v));
        }
        TypedValue tv = vals.remove("uuid");
        UUID guid = null;
        if(tv!=null)
          guid = UUID.fromString(tv.getGValue());
        return new GeneratorValueProvider(guid, vals);
      }
    });
    //constrs.put(ReferenceValueProvider.TYPE, ReferenceValueProvider.class);
  }
  public static IValueProvider initProvider(String type, JSONObject set, IRelatedFilesProvider resFolder){
    IVPCreator c = constrs.get(type);
    if(c==null)
      return null;
    IValueProvider cs = null;
    try {
      cs = c.create(set,resFolder);
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    if(cs==null)
      return null;
    return cs;
  }

  public static IValueProvider initProvider(UUID uid, String type, AttributeType atype, AttributeType etype, String enumKey, Map<String, Object> addParams, IRelatedFilesProvider resFolder, JsonReader reader){
    IVPCreator c = constrs.get(type);
    if(c==null)
      return null;
    IValueProvider cs = null;
    try {
      cs = c.create(uid, reader, atype, etype, enumKey, resFolder, addParams);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    if(cs==null)
      return null;
    return cs;
  }
  public static VPFactory getOne() {
    // TODO Auto-generated method stub
    return ft;
  }
}
