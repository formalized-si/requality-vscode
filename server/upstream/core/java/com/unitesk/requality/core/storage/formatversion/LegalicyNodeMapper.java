package com.unitesk.requality.core.storage.formatversion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.unitesk.jsonclone.JSONArray;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.jsonclone.LoadedJSONObject;
import com.unitesk.requality.core.api.RequalityStoredVersionProvider;
import com.unitesk.requality.core.attribute.AppliedEnumValue;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeGenerator;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.attribute.FVPDetectResult;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.Version;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.GeneratorValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.IRelatedFilesProvider;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.VPFactory;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.nodeiterator.AttributeValueIteratorType;
import com.unitesk.requality.core.storage.NodeMapper;

public class LegalicyNodeMapper {

  private static Object listFrom0_21_1JSON(JSONObject a) throws JSONException {
    if (!a.has("value")) return null;
    AttributeType ltype = AttributeType.STRING;
    if (a.has("elements_type")) ltype = AttributeType.valueOf(a.getString("elements_type"));
    ArrayList<Object> attrLst = new ArrayList<>();
    JSONArray vals = a.getJSONArray("value");
    for (int j = 0; j < vals.length(); j++) {
      Object o = vals.get(j);
      if (o instanceof JSONObject) attrLst.add(valueFrom0_21_1JSON(ltype, vals.getJSONObject(j)));
      else attrLst.add(o); //backward comp
    }
    return new ListValue(ltype, attrLst);
  }

  private static AppliedEnumValue appliedEnumFrom0_21_1JSON(JSONObject a) throws JSONException {
    if (!a.has("elements_type")) return null;
    AttributeType at = AttributeType.valueOf(a.getString("elements_type"));
    EnumValue ev = enumFrom0_21_1JSON(at, a);
    return new AppliedEnumValue(ev.getValue(), ev.getComment(), at, a.getString("enum_key"));
  }

  private static EnumDefinitionValue enumDefinitionFrom0_21_1JSON(JSONObject a) throws JSONException {
    if (!a.has("value")) return null;
    JSONArray vals = a.getJSONArray("value");

    AttributeType ltype = AttributeType.STRING;
    if (a.has("elements_type")) ltype = AttributeType.valueOf(a.getString("elements_type"));

    EnumDefinitionValue edv = new EnumDefinitionValue(ltype, new ArrayList<EnumValue>());

    for (int j = 0; j < vals.length(); j++)
      edv.addEnumValue(enumFrom0_21_1JSON(ltype, vals.getJSONObject(j)));

    //        if(ltype != AttributeType.LIST&&ltype != AttributeType.ENUM_DEFINITION)
    //          for (int j = 0; j < vals.length(); j++) {
    //              attrLst.put(ltype.fromString(vals.getString(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j));
    //          }
    //        else
    //        {
    //          if(ltype == AttributeType.LIST)
    //              for (int j = 0; j < vals.length(); j++) {
    //                  attrLst.put(listFromJSON(vals.getJSONObject(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j));
    //              }
    //
    //          if(ltype == AttributeType.ENUM_DEFINITION)
    //              for (int j = 0; j < vals.length(); j++) {
    //                  attrLst.put(enumDefinitionFromJSON(vals.getJSONObject(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j));
    //              }
    //        }

    return edv;
  }
  private static EnumValue enumFrom0_21_1JSON(AttributeType at, JSONObject a) throws JSONException {
    Object val = valueFrom0_21_1JSON(at, a);
    String comm = null;
    if (a.has("comment")) comm = a.getString("comment");
    return new EnumValue(val, comm);
  }

  private static Object valueFrom0_21_1JSON(AttributeType type, JSONObject a) throws JSONException {
    Object value;
    if (type == AttributeType.BOOL) {
      if (!a.has("value")) return null;
      String v = a.getString("value");
      if ("true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
        value = Boolean.valueOf(v);
      } else {
        value = v;
      }
    } else if (type == AttributeType.LIST) {
      value = listFrom0_21_1JSON(a);
    } else if (type == AttributeType.ENUM_DEFINITION) {
      value = enumDefinitionFrom0_21_1JSON(a);
    } else if (type == AttributeType.REFERENCE) {
      if (!a.has("value")) return null;
      value = a.getString("value");
    } else if (type == AttributeType.ENUM) {
      value = appliedEnumFrom0_21_1JSON(a);
    } else {
      if (!a.has("value")) return null;
      // TODO: all attribute types support
      String aString = a.optString("value", "");
      if(aString!=null)
        aString = aString.replace("${node.resURL}/", "");//clear old image-related strings
      try {
        if (!a.has("value")) value = AttributeType.getDefaultValue(type);
        else value = type.fromString(aString); 
      } catch (Exception e) {
        value = aString;
      }
    }
    return value;
  }

  private static FVPDetectResult detectFVP(String key, AttributeType type, Object val, IRelatedFilesProvider resFolder) {
    byte flag = AttributeDefinition.detectFlags(key);
    IValueProvider vp = AttributeDefinition.detectValueProvider(key,type,flag,val,null);//TODO use files provider but attributed one
    return new FVPDetectResult(vp, flag);
  }
  private static EnumValue enumFromJSON015(AttributeType at,JSONObject a) throws JSONException {
    Object val = valueFromJSON015(at, a);
    String comm = null;
    if(a.has("comment"))
        comm = a.getString("comment");
    return new EnumValue(val, comm);
}
  private static EnumDefinitionValue enumDefinitionFromJSON015(JSONObject a) throws JSONException {
    JSONArray vals = a.getJSONArray("value");
    
    AttributeType ltype = AttributeType.STRING;
    if(a.has("elements_type"))
        ltype = AttributeType.valueOf(a.getString("elements_type"));
    
    EnumDefinitionValue edv = new EnumDefinitionValue(ltype, new ArrayList<EnumValue>());

    for(int j = 0;j<vals.length();j++)
        edv.addEnumValue(enumFromJSON015(ltype, vals.getJSONObject(j)));
    
//    if(ltype != AttributeType.LIST&&ltype != AttributeType.ENUM_DEFINITION)
//      for (int j = 0; j < vals.length(); j++) {
//          attrLst.put(ltype.fromString(vals.getString(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j));
//      }
//    else
//    {
//      if(ltype == AttributeType.LIST)
//          for (int j = 0; j < vals.length(); j++) {
//              attrLst.put(listFromJSON(vals.getJSONObject(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j)); 
//          }
//
//      if(ltype == AttributeType.ENUM_DEFINITION)
//          for (int j = 0; j < vals.length(); j++) {
//              attrLst.put(enumDefinitionFromJSON(vals.getJSONObject(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j));
//          }
//    }

    return edv;
}

  private static Object valueFromJSON015(AttributeType type, JSONObject a) throws JSONException {
      Object value;
      if (type == AttributeType.BOOL) {
          String v = a.optString("value");
          if(v.isEmpty())
            value = false;
          else
            if ("true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
                value = Boolean.valueOf(v);
            } else {
                value = v;
            }
      } else if (type == AttributeType.LIST) {
          value = listFromJSON015(a);
      } else if (type == AttributeType.ENUM_DEFINITION){
          value = enumDefinitionFromJSON015(a);
      } else if (type == AttributeType.REFERENCE) {
          value = a.optString("value");
      } else if (type == AttributeType.ENUM){
          value = appliedEnumFromJSON015(a);
      } else {
          // TODO: all attribute types support
          try {
              value = type.fromString(a.optString("value"));
          } catch (Exception e) {
              value = a.optString("value");
          }
      }
      return value;
  }

  private static AppliedEnumValue appliedEnumFromJSON015(JSONObject a) throws JSONException {
      AttributeType at = AttributeType.STRING;
      if(a.has("elements_type"))
        at = AttributeType.valueOf(a.getString("elements_type"));
      EnumValue ev = enumFromJSON015(at, a);
      String key = a.optString("enum_key");
      if(key.isEmpty())
        key = a.optString("key");
      return new AppliedEnumValue(ev.getValue(), ev.getComment(),at, key);
  }

  private static Object listFromJSON015(JSONObject a) throws JSONException {
      AttributeType ltype = AttributeType.STRING;
      if(a.has("elements_type"))
          ltype = AttributeType.valueOf(a.getString("elements_type"));
      ArrayList<Object> attrLst = new ArrayList<Object>();
      JSONArray vals = a.optJSONArray("value");
      if(vals!=null)
        for (int j = 0; j < vals.length(); j++){
            Object o = vals.get(j);
            if(o instanceof JSONObject)
                attrLst.add(valueFromJSON015(ltype, vals.getJSONObject(j)));
            else
                attrLst.add(o);//backward comp
        }
      else
        RLogger.logDebug("Empty array "+a, 7); 
      return new ListValue(ltype,attrLst);
  }
  private static void load015(UUID uuid, List<AttributeDefinition> result, Object attrO, IRelatedFilesProvider resFolder)
         throws JSONException {
    Map<String, JSONObject> attrs = new HashMap<>();
    if(attrO instanceof JSONObject) {
      JSONObject jo = (JSONObject) attrO;
      for (String jkey : JSONObject.getNames(jo)) {
        JSONObject a = jo.getJSONObject(jkey);
        String key = jo.optString("key", jkey);
        attrs.put(key,a);
      }
    }else if (attrO instanceof JSONArray) {
      JSONArray ja = (JSONArray) attrO;
      for(int i = 0;i<ja.length();i++) {
        JSONObject jo = ja.optJSONObject(i);
        if(jo!=null)
          attrs.put(jo.getString("key"), jo);
      }
    }
    for (Entry<String, JSONObject> en: attrs.entrySet()) {
      JSONObject a = en.getValue();
      String key = en.getKey();
          UUID origin = uuid;
          AttributeType type = AttributeType.STRING;
          boolean istp = false;
          if(a.has("type")){
            String s = a.getString("type");
            try{
                type = AttributeType.valueOf(s);
                istp = true;
            }
            catch(Exception e){
                //generated
            }
          }
          if (key.length() > 37) { //length of uuid plus "-", for old format of json keys
            if (key.charAt(36) == '-') {
              try {
                origin = UUID.fromString(key.substring(0, 36));
                key = key.substring(37);
              } catch (Exception e) {
              }
            }
          }
          Object value = null;
          AttributeType ltype = null;
          JSONObject valuefrom = a;
          if(!istp){
            JSONObject jo = a.optJSONObject("value");//TODO check
            if(jo!=null){
              valuefrom = jo;
              String at = jo.optString("atype", AttributeType.STRING.toString());
              if(at!=null){
                type = AttributeType.valueOf(at);
              }
            }
          }
          value = valueFromJSON015(type, valuefrom);
          
          try {
              String or = a.getString("origin");
              origin = UUID.fromString(or);
          } catch (Exception e) {
          }
          AttributeAvailability pa = AttributeAvailability.LOCAL;
          try{
              String avail = a.getString("availability");
              pa = AttributeAvailability.valueOf(avail);
              
          }catch(Exception e){
          }
          AttributeDefinition rattr;

          /*Version v = Version.ZERO;
          if(a.has("version")){
              v = new Version(a.getLong("version"));
          }*/
          if(a.has("generator")){
              JSONObject obj = a.getJSONObject("generator");
              Map<String,TypedValue> setts = new HashMap<>();
              JSONObject set = obj.getJSONObject("settings");
              JSONArray names = set.names();
              if(names!=null)
                  for(int i = 0;i<names.length();i++){
                      try{
                          JSONObject jo = set.getJSONObject(names.getString(i));
                          setts.put(names.getString(i), new TypedValue(AttributeType.LIST,listFromJSON015(jo)));
                      }
                      catch(JSONException e){
                          setts.put(names.getString(i), new TypedValue(AttributeType.STRING,set.get(names.getString(i)))); 
                      }
                  }
              AttributeType at = AttributeType.STRING;
              if(obj.has("attribute_type"))
                  at = AttributeType.values()[Integer.parseInt(obj.getString("attribute_type"))+1];
              value = valueFromJSON015(at, a);
              //rattr = new Attribute(v,origin, type, key, value);
              AttributeGenerator ag = new AttributeGenerator(UUID.fromString(obj.getString("uuid")),setts);
              rattr = new AttributeDefinition(origin, key, pa, new GeneratorValueProvider(ag), AttributeDefinition.detectFlags(key));
              //genObj.put("atribute_type", ag.getAttributeType().ordinal());
              //genObj.put("atribute_list_type", ag.getAttributeListType().ordinal());
          }else
              rattr = new AttributeDefinition(origin, key, pa, AttributeDefinition.detectValueProvider(key, type, value, resFolder),
                  AttributeDefinition.detectFlags(key));
          result.add(rattr);
      }
  }
  public static Pair<String,Map<String, AttributeDefinition>> getAttributes(
      UUID uuid, byte[] content, IRelatedFilesProvider resFolder, String version) throws JSONException, IOException {
    return getAttributes(uuid, new IInputStreamProvider(){

      @Override
      public InputStream getStream() {
        return new ByteArrayInputStream(content);
      }
      
    }, resFolder, version);
  }

  public static Pair<String,Map<String, AttributeDefinition>> getAttributes(
      UUID uuid, IInputStreamProvider prov, IRelatedFilesProvider resFolder, String version) throws JSONException {
    if (prov == null) {
      return null;
    }
    JSONObject obj = null;
    try {
      List<AttributeDefinition> result = new LinkedList<>();
      String ver = version;
      try(InputStream is = prov.getStream()){
        String latest = RequalityStoredVersionProvider.getVersion();
        if(version==null || version.equals(latest)){
          return new Pair<>(latest, NodeMapper.getAttributes(uuid, is, resFolder));
        }
      }catch(Exception az){
      }
      try(InputStream is = prov.getStream()){
        obj = new LoadedJSONObject(is);
      } catch (IOException e) {
        RLogger.logDebug(e,1);
      }catch (JSONException e) {
        RLogger.logDebug(e,1);
      }
      if(obj == null)
        return null;//TODO exception with details
      Object attrObj = obj.get("attributes");
      ver = tryLoadOldFormat(ver, uuid, result, attrObj, resFolder);
      Map<String, AttributeDefinition> ret = new HashMap<>();
      for (AttributeDefinition a : result) {
        //ret.put(a.getKey(),AttributeStorage.addToList(ret,ret.get(a.getKey()), a, uuid).);
        if (!ret.containsKey(a.getKey())) 
          ret.put(a.getKey(), a);
        else {
          AttributeDefinition b = ret.get(a.getKey());
          if (a.isMoreVisible(null, b, uuid, true)) ret.put(a.getKey(), a);
        }
        //ret.put(a.getKey(),AttributeStorage.addToList(ret,ret.get(a.getKey()), a, uuid));
        //ret.get(a.getKey()).add(a);
        //TODO check order
  
      }
      return new Pair<String, Map<String,AttributeDefinition>>(ver, ret);
    }catch(Exception e) {
      e.printStackTrace();
      throw e;
    }
    finally {
      if(obj!=null)
        obj.close();
    }
  }
  private static String tryLoadOldFormat(
     String version, UUID uuid, List<AttributeDefinition> result, Object attrObj, IRelatedFilesProvider resFolder) throws JSONException {
    if(attrObj == null)
      throw new RuntimeException("attributes is not found for "+uuid);
    if(version == null)
      try{
        loadStrange0_21_2(uuid, result, attrObj, resFolder);
        return "0.21.2*";
      }catch(Exception e){
        try{
          loadProper0_21_2(uuid, result, attrObj, resFolder);
          return "0.21.2";
        }catch(Exception r){
          try {
            load0_21_1(uuid, result, attrObj, resFolder);
            return "0.21.1";
          }catch(Exception ea) {
            try{
              load0_20 (uuid, result,attrObj, resFolder);
              return "0.20";
            }
            catch(Exception e1){
              try{
                load015(uuid, result, attrObj, resFolder);
                return "0.15";
              }catch(Exception e2){
                try{
                  loadSemiOldFormat(uuid, result, attrObj, resFolder);
                }catch(Exception e4){
                  loadOldFormat(uuid, result, attrObj, resFolder);
                }
                return "undefined";
              }
            }
          }
        }
      }
    else{
      String ver = version;
      if(version.equals("0.21.2"))
      try {
          loadStrange0_21_2(uuid, result, attrObj, resFolder);
      }catch(Exception e) {
          result.clear();
          loadProper0_21_2(uuid, result, attrObj, resFolder);
      }
      else
        if(version.equals("0.21.1")|| version.equals("0.21"))
          load0_21_1(uuid, result, attrObj, resFolder);
        else
          if(version.equals("0.20"))
            load0_20(uuid, result, attrObj, resFolder);
          else
            if(version.equals("0.15"))
              load015(uuid, result,attrObj,resFolder);
            else
              try{
                loadSemiOldFormat(uuid, result, attrObj, resFolder);
              }catch(Exception r){
                loadOldFormat(uuid, result, attrObj, resFolder);
              }
    }
    return version;
  }
  private static Object valueFromJSON020(AttributeType type, JSONObject a) throws JSONException {
      Object value;
      if (type == AttributeType.BOOL) {
          String v = a.getString("value");
          if ("true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
              value = Boolean.valueOf(v);
          } else {
              value = v;
          }
      } else if (type == AttributeType.LIST) {
          value = listFromJSON020(a);
      } else if (type == AttributeType.ENUM_DEFINITION){
          value = enumDefinitionFromJSON020(a);
      } else if (type == AttributeType.REFERENCE) {
          value = a.getString("value");
      } else if (type == AttributeType.ENUM){
          value = appliedEnumFromJSON020(a);
      } else {
          // TODO: all attribute types support
          try {
              value = type.fromString(a.getString("value"));
          } catch (Exception e) {
              value = a.getString("value");
          }
      }
      return value;
  }
  private static EnumDefinitionValue enumDefinitionFromJSON020(JSONObject a) throws JSONException {
    JSONArray vals = a.getJSONArray("value");
    
    AttributeType ltype = AttributeType.STRING;
    if(a.has("elements_type"))
        ltype = AttributeType.valueOf(a.getString("elements_type"));
    
    EnumDefinitionValue edv = new EnumDefinitionValue(ltype, new ArrayList<EnumValue>());

    for(int j = 0;j<vals.length();j++)
        edv.addEnumValue(enumFromJSON020(ltype, vals.getJSONObject(j)));
    
//    if(ltype != AttributeType.LIST&&ltype != AttributeType.ENUM_DEFINITION)
//      for (int j = 0; j < vals.length(); j++) {
//          attrLst.put(ltype.fromString(vals.getString(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j));
//      }
//    else
//    {
//      if(ltype == AttributeType.LIST)
//          for (int j = 0; j < vals.length(); j++) {
//              attrLst.put(listFromJSON(vals.getJSONObject(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j)); 
//          }
//
//      if(ltype == AttributeType.ENUM_DEFINITION)
//          for (int j = 0; j < vals.length(); j++) {
//              attrLst.put(enumDefinitionFromJSON(vals.getJSONObject(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j));
//          }
//    }

    return edv;
}

  private static EnumValue enumFromJSON020(AttributeType at,JSONObject a) throws JSONException {
    Object val = valueFromJSON020(at, a);
    String comm = null;
    if(a.has("comment"))
        comm = a.getString("comment");
    return new EnumValue(val, comm);
}
    
  private static AppliedEnumValue appliedEnumFromJSON020(JSONObject a) throws JSONException {
      AttributeType at = AttributeType.valueOf(a.getString("elements_type"));
      EnumValue ev = enumFromJSON020(at, a);
      return new AppliedEnumValue(ev.getValue(), ev.getComment(),at, a.getString("enum_key"));
  }

  private static Object listFromJSON020(JSONObject a) throws JSONException {
      AttributeType ltype = AttributeType.STRING;
      if(a.has("elements_type"))
          ltype = AttributeType.valueOf(a.getString("elements_type"));
      ArrayList<Object> attrLst = new ArrayList<Object>();
      JSONArray vals = a.getJSONArray("value");
      for (int j = 0; j < vals.length(); j++){
          Object o = vals.get(j);
          if(o instanceof JSONObject)
              attrLst.add(valueFromJSON020(ltype, vals.getJSONObject(j)));
          else
              attrLst.add(o);//backward comp
      }
      return new ListValue(ltype,attrLst);
  }
  private static void load0_20(UUID uuid, List<AttributeDefinition> result, Object attrO, IRelatedFilesProvider resFolder) throws JSONException {
    if(! (attrO instanceof JSONObject))
        throw new RuntimeException ("attributes are not an JSON Object for "+uuid);
      JSONObject attr = (JSONObject)attrO;
    for (String key : JSONObject.getNames(attr)) {
      JSONObject a = attr.getJSONObject(key);
      UUID origin = uuid;
      AttributeType type = AttributeType.valueOf(a.getString("type"));
      Object value = null;
      AttributeType ltype = null;
      value = valueFromJSON020(type, a);
      if (key.length() > 37) { //length of uuid plus "-", for old format of json keys
        if (key.charAt(36) == '-') {
          try {
            origin = UUID.fromString(key.substring(0, 36));
            key = key.substring(37);
          } catch (Exception e) {
          }
        }
      }
      try {
          String or = a.getString("origin");
          origin = UUID.fromString(or);
      } catch (Exception e) {
      }
      AttributeAvailability pa = AttributeAvailability.LOCAL;
      try{
          String avail = a.getString("availability");
          pa = AttributeAvailability.valueOf(avail);
          
      }catch(Exception e){
      }
      AttributeDefinition rattr;
      if(a.has("generator")){
          JSONObject obj = a.getJSONObject("generator");
          Map<String,TypedValue> setts = new HashMap<String, TypedValue>();
          JSONObject set = obj.getJSONObject("settings");
          JSONArray names = set.names();
          if(names!=null)
              for(int i = 0;i<names.length();i++){
                  try{
                      JSONObject jo = set.getJSONObject(names.getString(i));
                      setts.put(names.getString(i), new TypedValue(AttributeType.LIST,jo));
                  }
                  catch(JSONException e){
                      setts.put(names.getString(i), new TypedValue(AttributeType.STRING, names.getString(i))); 
                  }
              }
          AttributeType at = AttributeType.STRING;
          if(obj.has("attribute_type"))
              at = AttributeType.values()[Integer.parseInt(obj.getString("attribute_type"))+1];
          value = valueFromJSON020(at, a);
          //rattr = new AttributeDefinition(origin, key, vp);
          UUID guid = UUID.fromString(obj.getString("uuid"));
          AttributeGenerator ag = new AttributeGenerator(guid, setts);
          //AttributeGenerator ag = new AttributeGenerator(rattr, ),setts);
          //ag.setGeneratorType(AttributeValueIteratorType.fromId(obj.getString("generator_type")));
          rattr = new AttributeDefinition(origin, key, pa, new GeneratorValueProvider(ag), AttributeDefinition.detectFlags(key));
          //genObj.put("atribute_type", ag.getAttributeType().ordinal());
          //genObj.put("atribute_list_type", ag.getAttributeListType().ordinal());
      }else{
        IValueProvider vp = AttributeDefinition.detectValueProvider(key, type, value, resFolder);
        rattr = new AttributeDefinition(origin, key, pa, vp, AttributeDefinition.detectFlags(key));
      }
      result.add(rattr);
  }
  }

  private static void loadStrange0_21_2(
      UUID uuid, List<AttributeDefinition> result, Object attrO, IRelatedFilesProvider resFolder)
      throws JSONException {
    if(! (attrO instanceof JSONObject))
      throw new RuntimeException ("attributes are not an JSON Object for "+uuid);
    JSONObject attr = (JSONObject)attrO;
    String[] jnames = JSONObject.getNames(attr);
    if (jnames == null || jnames.length == 0)
      throw new RuntimeException("attribute names are not found for "+uuid);
    for (String jkey : jnames) {
      JSONObject a = attr.getJSONObject(jkey);
      UUID origin = uuid;
      String key = jkey;
      if (a.has("key")) {
        key = a.getString("key");
      }
      if (a.has("origin")) {
        String or = a.getString("origin");
        origin = UUID.fromString(or);
      }

      AttributeAvailability pa = AttributeAvailability.LOCAL;
      if (a.has("availability")) {
        String avail = a.getString("availability");
        pa = AttributeAvailability.valueOf(avail);
      }
      int flag = 0;
      if(a.has("flag"))
        flag = a.getInt("flag");
      AttributeDefinition rattr;
      if(a.has("valueProvider")){
        IValueProvider vp = jsonToValueProvider(uuid, a.getJSONObject("valueProvider"), resFolder);
        rattr = new AttributeDefinition(origin,key,pa,vp,flag);
        result.add(rattr);
      }else
        throw new RuntimeException("wrong format!");
    }
  }
  private static void loadProper0_21_2(
      UUID uuid, List<AttributeDefinition> result, Object attrO, IRelatedFilesProvider resFolder)
      throws JSONException {
    if(! (attrO instanceof JSONObject))
        throw new RuntimeException ("attributes are not an JSON Object for "+uuid);
      JSONObject attr = (JSONObject)attrO;
    String[] jnames = JSONObject.getNames(attr);
    if (jnames == null || jnames.length == 0)
      throw new RuntimeException("attribute names are not found for "+uuid);

    for (String jkey : jnames) {
      JSONObject a = attr.getJSONObject(jkey);
      UUID origin = uuid;
      String key = jkey;
      if (a.has("key")) {
        key = a.getString("key");
      }
      if (a.has("origin")) {
        String or = a.getString("origin");
        origin = UUID.fromString(or);
      }

      AttributeAvailability pa = AttributeAvailability.LOCAL;
      if (a.has("availability")) {
        String avail = a.getString("availability");
        pa = AttributeAvailability.valueOf(avail);
      }
      int flag = 0;
      if(a.has("flag"))
        flag = a.getInt("flag");
      AttributeDefinition rattr;
      if(a.has("value")){
      Object val = a.get("value");
      if(!(val instanceof JSONObject))
      {
      
          if(val instanceof Boolean) {
            JSONObject jo = new JSONObject();
            jo.put("value", (Boolean)val);
            jo.put("atype", AttributeType.BOOL);
            val = jo;
          }else
          if(val instanceof Integer) {
            JSONObject jo = new JSONObject();
            jo.put("value", (Integer)val);
            jo.put("atype", AttributeType.INT);
            val = jo;
          }else if(val instanceof Double) {
            JSONObject jo = new JSONObject();
            jo.put("value", (Double)val);
            jo.put("atype", AttributeType.FLOAT);
            val = jo;
          }else if(val instanceof String) {
            String sval = ((String) val).trim();
            if(sval.startsWith("{")){
              val = new JSONObject(val);//TODO test
            }else
            {
//                if(!sval.startsWith("\""))
//                  sval = "\""+sval+"\"";
              JSONObject jo = new JSONObject();
              jo.put("value", val);
                //TODO here tends to be problem sometimes
            }
          }
      }
      IValueProvider vp = jsonToValueProvider(uuid, (JSONObject)val, resFolder);
        rattr = new AttributeDefinition(origin,key,pa,vp,flag);
        result.add(rattr);
      }else
        throw new RuntimeException("wrong format!");
    }
  }
  private static IValueProvider jsonToValueProvider(
      UUID or, JSONObject attrObj, IRelatedFilesProvider resFolder) throws JSONException {
    //JSONObject vpo = new JSONObject();
    //attrObj.put("valueProvider", vpo);
    String vt = ConstValueProvider.TYPE;
    if(attrObj.has("type")){
      JSONObject tp = attrObj.optJSONObject("type");
      if(tp != null)
        vt = tp.getString("value");//strange part
      else
        vt = attrObj.getString("type");
    }
    //if(attrObj.has("atype"))
      //  at = AttributeType.valueOf(attrObj.getString("atype"));
    ////Object o = valueFromJSON(at, attrObj);
    //Map<String, Object> setts = new HashMap<>();
    //JSONObject set = new JSONObject();
  //  if(attrObj.has("settings"))
  //    set = attrObj.getJSONObject("settings");
  ////  JSONArray names = set.names();
  //  if (names != null)
  //    for (int i = 0; i < names.length(); i++) {
  //      try {
  //        JSONObject jo = set.getJSONObject(names.getString(i));
  //        setts.put(names.getString(i), listFromJSON(jo));
  //      } catch (JSONException e) {
  //        setts.put(names.getString(i), set.get(names.getString(i)));
  //      }
  //    }
    //if(attrObj.has("value"))
      //attrObj = attrObj.getJSONObject("value");
    return VPFactory.initProvider(vt, attrObj, resFolder);
  }
  private static void load0_21_1(
      UUID uuid, List<AttributeDefinition> result, Object attrO, IRelatedFilesProvider resFolder)
      throws JSONException {
    if(! (attrO instanceof JSONObject))
        throw new RuntimeException ("attributes are not an JSON Object for "+uuid);
      JSONObject attr = (JSONObject)attrO;
    String[] jnames = JSONObject.getNames(attr);
    if (jnames == null || jnames.length == 0) 
      throw new RuntimeException("Attribute names are not found for "+uuid);
    for (String jkey : jnames) {
      JSONObject a = attr.getJSONObject(jkey);
      UUID origin = uuid;
      AttributeType type = AttributeType.STRING;
      String key = jkey;
      if (key.length() > 37) { //length of uuid plus "-", for old format of json keys
        if (key.charAt(36) == '-') {
          try {
            origin = UUID.fromString(key.substring(0, 36));
            key = key.substring(37);
          } catch (Exception e) {
          }
        }
      }
      if (a.has("key")) {
        key = a.getString("key");
      }
      if (a.has("type")) {
        String s = a.getString("type");
        type = AttributeType.valueOf(s);
      }
      Object value = null;
      value = valueFrom0_21_1JSON(type, a);
      if (a.has("origin")) {
        String or = a.getString("origin");
        origin = UUID.fromString(or);
      }
      AttributeAvailability pa = AttributeAvailability.LOCAL;
      if (a.has("availability")) {
        String avail = a.getString("availability");
        pa = AttributeAvailability.valueOf(avail);
      }
      AttributeDefinition rattr;
      Version v = Version.getZero();
      if (a.has("version")) {
        v = Version.fromString((a.getString("version")));
      }
      if (a.has("generator")) {
        JSONObject obj = a.getJSONObject("generator");
        Map<String, TypedValue> setts = new HashMap<>();
        JSONObject set = obj.getJSONObject("settings");
        JSONArray names = set.names();
        if (names != null)
          for (int i = 0; i < names.length(); i++) {
            try {
              JSONObject jo = set.getJSONObject(names.getString(i));
              setts.put(names.getString(i), new TypedValue(AttributeType.LIST, listFrom0_21_1JSON(jo)));
            } catch (JSONException e) {
              setts.put(names.getString(i), Attribute.getTypedValue(set.get(names.getString(i))));
            }
          }
        AttributeType at = AttributeType.STRING;
        if (obj.has("attribute_type"))
          at = AttributeType.values()[Integer.parseInt(obj.getString("attribute_type"))+1];
        value = valueFrom0_21_1JSON(at, a);
        //rattr = new Attribute(v,origin, type, key, value);
        AttributeValueIteratorType gt = AttributeValueIteratorType.FORMULA;
        if(obj.has("generator_type")){
          String go = obj.getString("generator_type");
          if(go!=null)
            gt = AttributeValueIteratorType.fromId(go);
        }
        setts.put("generator_type", new TypedValue(AttributeType.STRING,gt.getId()));
        AttributeGenerator ag =
            new AttributeGenerator(UUID.fromString(obj.getString("uuid")), setts);
        //ag.setGeneratorType();
        rattr = new AttributeDefinition(origin, key, pa, new GeneratorValueProvider(ag),AttributeDefinition.detectFlags(key));
        //genObj.put("atribute_type", ag.getAttributeType().ordinal());
        //genObj.put("atribute_list_type", ag.getAttributeListType().ordinal());
     } else{
       FVPDetectResult det =  detectFVP(key, type, value, resFolder);
       rattr = new AttributeDefinition(origin, key,pa,det.valueProvider, det.flag);
     }
     // rattr.setAvailability(pa);
      result.add(rattr);
    }
    if(result.size() == 0)
      throw new RuntimeException("Attribute set is empty for "+uuid);
  }

  private static void loadSemiOldFormat(UUID uuid, List<AttributeDefinition> result, Object attrO, IRelatedFilesProvider resFolder)
      throws JSONException {
    Map<String, JSONObject> attrs = new HashMap<>();
    if(attrO instanceof JSONObject) {
      JSONObject jo = (JSONObject) attrO;
      for (String jkey : JSONObject.getNames(jo)) {
        JSONObject a = jo.getJSONObject(jkey);
        String key = jo.optString("key", jkey);
        attrs.put(key,a);
      }
    }else if (attrO instanceof JSONArray) {
      JSONArray ja = (JSONArray) attrO;
      for(int i = 0;i<ja.length();i++) {
        JSONObject jo = ja.optJSONObject(i);
        if(jo!=null)
          attrs.put(jo.getString("key"), jo);
      }
    }
    for (Entry<String, JSONObject> en: attrs.entrySet()) {
      JSONObject a = en.getValue();
      String key = en.getKey();
      UUID origin = uuid;
      AttributeType type = AttributeType.valueOf(a.getString("type"));
      Object value = null;
      if (type == AttributeType.BOOL) {
        String v = a.getString("value");
        if ("true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
          value = Boolean.valueOf(v);
        } else {
          value = v;
        }
      } else if (type == AttributeType.LIST) {
        value = oldListFromJSON(a);
      } else if (type == AttributeType.ENUM_DEFINITION) {
        value = oldMapFromJSON(a);
      } else if (type == AttributeType.REFERENCE) {
        value = a.getString("value");
      } else if (type == AttributeType.ENUM) {
        String comm = null;
        if (a.has("comment")) comm = a.getString("comment");
        value =
            new AppliedEnumValue(
                a.getString("last_value"),
                comm,
                AttributeType.valueOf(a.getString("elements_type")),
                a.getString("enum_key"));
      } else {
        // TODO: all attribute types support
        try {
          value = type.fromString(a.getString("value"));
        } catch (Exception e) {
          value = a.getString("value");
        }
      }

      try {
        String or = a.getString("origin");
        origin = UUID.fromString(or);
      } catch (Exception e) {
      }
      AttributeAvailability pa = AttributeAvailability.LOCAL;
      try {
        String avail = a.getString("availability");
        pa = AttributeAvailability.valueOf(avail);

      } catch (Exception e) {
      }

      Version v = Version.getZero();
      if (a.has("version")) {
        v = Version.fromString(a.getString("version"));
      }
      
      AttributeDefinition rattr;
      if (a.has("generator")) {
        JSONObject obj = a.getJSONObject("generator");
        Map<String, TypedValue> setts = new HashMap<>();
        JSONObject set = obj.getJSONObject("settings");
        JSONArray names = set.names();
        if (names != null)
          for (int i = 0; i < names.length(); i++) {
            try {
              JSONObject jo = set.getJSONObject(names.getString(i));
              setts.put(names.getString(i), new TypedValue(AttributeType.LIST, listFrom0_21_1JSON(jo)));
            } catch (JSONException e) {
              setts.put(names.getString(i), Attribute.getTypedValue(set.get(names.getString(i))));
            }
          }
        AttributeType at = AttributeType.STRING;
        if (obj.has("atribute_type"))
          at = AttributeType.values()[Integer.parseInt(obj.getString("atribute_type"))+1];
        value = valueFrom0_21_1JSON(at, a);
        //rattr = new Attribute(v,origin, type, key, value);
        AttributeGenerator ag =
            new AttributeGenerator(UUID.fromString(obj.getString("uuid")), setts);
        ag.setGeneratorType(AttributeValueIteratorType.fromId(obj.getString("generator_type")));
        rattr = new AttributeDefinition(uuid, key, pa, new GeneratorValueProvider(ag), AttributeDefinition.detectFlags(key));//(false, v, ag.getMasterUUId(), at, key, null, value, ag);
        //ag.setAttribute(rattr);
        //genObj.put("atribute_type", ag.getAttributeType().ordinal());
        //genObj.put("atribute_list_type", ag.getAttributeListType().ordinal());
      } else {
        FVPDetectResult res = detectFVP(key, type, value, resFolder);
        rattr = new AttributeDefinition(origin,key,pa,res.valueProvider, res.flag);
      }
      //rattr.setAvailability(pa);
      result.add(rattr);
    }
  }

  private static Object oldListFromJSON(JSONObject a) throws JSONException {
    ArrayList<Object> attrLst = new ArrayList<>();
    JSONArray vals = a.getJSONArray("value");
    AttributeType ltype = AttributeType.STRING;
    if (a.has("elements_type")) ltype = AttributeType.valueOf(a.getString("elements_type"));
    if (ltype != AttributeType.LIST && ltype != AttributeType.ENUM_DEFINITION)
      for (int j = 0; j < vals.length(); j++) {
        attrLst.add(ltype.fromString(vals.getString(j)));
      }
    else {
      if (ltype == AttributeType.LIST)
        for (int j = 0; j < vals.length(); j++) {
          attrLst.add(oldListFromJSON(vals.getJSONObject(j)));
        }
      if (ltype == AttributeType.ENUM_DEFINITION)
        for (int j = 0; j < vals.length(); j++) {
          attrLst.add(oldMapFromJSON(vals.getJSONObject(j)));
        }
    }
    return new ListValue(ltype, attrLst);
  }

  private static Object oldMapFromJSON(JSONObject a) throws JSONException {
    List<EnumValue> evals = new ArrayList<>();
    JSONArray vals = a.getJSONArray("value");
    JSONArray valsComments = null;
    if (a.has("comments")) valsComments = a.getJSONArray("comments");

    AttributeType ltype = AttributeType.STRING;
    if (a.has("elements_type")) ltype = AttributeType.valueOf(a.getString("elements_type"));
    if (ltype != AttributeType.LIST && ltype != AttributeType.ENUM_DEFINITION)
      for (int j = 0; j < vals.length(); j++) {
        evals.add(
            new EnumValue(
                vals.getString(j),
                valsComments == null
                    ? null
                    : valsComments.getString(j).equals("") ? null : valsComments.getString(j)));
      }
    else {
      if (ltype == AttributeType.LIST)
        for (int j = 0; j < vals.length(); j++) {
          evals.add(
              new EnumValue(
                  oldListFromJSON(vals.getJSONObject(j)),
                  valsComments == null
                      ? null
                      : valsComments.getString(j).equals("") ? null : valsComments.getString(j)));
        }

      if (ltype == AttributeType.ENUM_DEFINITION)
        for (int j = 0; j < vals.length(); j++) {
          evals.add(
              new EnumValue(
                  oldMapFromJSON(vals.getJSONObject(j)),
                  valsComments == null
                      ? null
                      : valsComments.getString(j).equals("") ? null : valsComments.getString(j)));
        }
    }

    return new EnumDefinitionValue(ltype, evals);
  }

  private static void loadOldFormat(UUID uuid, List<AttributeDefinition> result, Object attrO, IRelatedFilesProvider resFolder)
      throws JSONException {
    Map<String, JSONObject> attrs = new HashMap<>();
    if(attrO instanceof JSONObject) {
      JSONObject jo = (JSONObject) attrO;
      for (String jkey : JSONObject.getNames(jo)) {
        JSONObject a = jo.getJSONObject(jkey);
        String key = jo.optString("key", jkey);
        attrs.put(key,a);
      }
    }else if (attrO instanceof JSONArray) {
      JSONArray ja = (JSONArray) attrO;
      for(int i = 0;i<ja.length();i++) {
        JSONObject jo = ja.optJSONObject(i);
        if(jo!=null)
          attrs.put(jo.getString("key"), jo);
      }
    }
    for (Entry<String, JSONObject> en: attrs.entrySet()) {
      JSONObject a = en.getValue();
      String key = en.getKey();
      AttributeType type = AttributeType.valueOf(a.getString("type"));
      Object value = null;
      Version v = Version.getZero();
      if (a.has("version")) {
        v = Version.fromString(a.getString("version"));
      }
      if (type == AttributeType.LIST) {
        ArrayList<Object> attrLst = new ArrayList<>();
        JSONArray vals = a.getJSONArray("value");
        for (int j = 0; j < vals.length(); j++) {
          attrLst.add(vals.getString(j));
        }
        value = new ListValue(AttributeType.STRING, attrLst);
      } else {
        // TODO: all attribute types support
        value = type.fromString(a.getString("value"));
      }
      FVPDetectResult res = detectFVP(key, type, value, resFolder);
      result.add(new AttributeDefinition(uuid, key, AttributeAvailability.LOCAL, res.valueProvider, res.flag));
    }
  }

  public static final JSONObject toJSON(TreeNode node) throws JSONException {
    return toJSON(node.getUUId(), node.attrs().getLMap().values()); //TODO
  }

  public static final JSONObject toJSON(UUID uuid, Collection<Attribute> collection)
      throws JSONException {
    return toJSON(uuid, collection, false);
  }
  public static AttributeType adapt(AttributeType at){
    if(at == AttributeType.HTML)
      return AttributeType.STRING;
    return at;
  }
  public static final JSONObject toJSON(UUID uuid, Collection<Attribute> collection, boolean kdup)
      throws JSONException {
    JSONObject result = null;
    result = new JSONObject();
    result.put("uuid", uuid);
    JSONObject attrs = new JSONObject();
    for (Attribute a : collection) {
      if(a.getDefinition().getRawValue()==null)
        continue;
      if (!a.getOrigin().equals(uuid) && !a.getKey().startsWith("_")) continue;
      if (a.getKey().startsWith("_%id")) continue; //ignore virt attr
      JSONObject attrObj = new JSONObject();
      //attrObj.put("key", a.getKey());
      if (a.getType() != AttributeType.STRING) attrObj.put("type", adapt(a.getType()));
      if (!a.getOrigin().equals(uuid)) attrObj.put("origin", a.getOrigin());
      //attrObj.put("version", a.getVersion().toString());
      valueToJSON(attrObj, adapt(a.getType()), adapt(a.getType()), a.getDefinition().getRawValue().getGValue());
      if (!a.getAvailability().equals(AttributeAvailability.LOCAL))
        attrObj.put("availability", a.getAvailability().name());
      AttributeGenerator ag = null;//a.getGenerator();
      if(a.getDefinition().getValueProvider() instanceof GeneratorValueProvider)
        ag = ((GeneratorValueProvider)a.getDefinition().getValueProvider()).getGenerator();
      if (ag != null) {
        JSONObject genObj = new JSONObject();
        genObj.put("uuid", ag.getUUID());
        genObj.put("attribute_type", adapt(ag.getAttributeType()).ordinal());
        if (ag.getAttributeListType() != null)
          genObj.put("atribute_list_type", ag.getAttributeListType().ordinal());
        genObj.put("generator_type", ag.getGeneratorType().getId());
        JSONObject genAttrs = new JSONObject();
        Map<String, TypedValue> setts = ag.getSettings();
        for (String s : setts.keySet())
          if (setts.get(s).getType()==AttributeType.LIST) {
            JSONObject val = new JSONObject();
            valueToJSON(val, AttributeType.LIST, AttributeType.LIST, setts.get(s).getValue());
            genAttrs.put(s, val);
          } else genAttrs.put(s, setts.get(s));
        genObj.put("settings", genAttrs);
        attrObj.put("generator", genObj);
      }
      attrs.put(kdup ? a.getKey() : (a.getKey()), attrObj);
    }
    result.put("attributes", attrs);
    return result;
  }

  private static void valueToJSON(
      JSONObject attrObj, AttributeType rawType, AttributeType resType, Object rawValue)
      throws JSONException {
    if (rawType.equals(AttributeType.ENUM)) {
      AppliedEnumValue aev = (AppliedEnumValue) rawValue;
      if (aev == null) return;
      attrObj.put("elements_type", adapt(aev.getType()));
      attrObj.put("enum_key", aev.getBaseKey());
      attrObj.put("comment", aev.getComment());
      valueToJSON(attrObj, adapt(aev.getType()), adapt(aev.getType()), aev.getValue());
      //           if(rawValue!=null)
      //             attrObj.put("rawvalue",aev.toStored());
    } else if (resType == AttributeType.LIST) {
      JSONArray lst = new JSONArray();
      ListValue lv = (ListValue) rawValue;
      if (rawValue == null) return;

      //Object[]val = (Object[])rawValue;
      AttributeType at = adapt(lv.getType());
      attrObj.put("elements_type", at);
      for (Object o : lv) {
        JSONObject jso = new JSONObject();
        valueToJSON(jso, at, at, o);
        lst.put(jso);
      }
      attrObj.put("value", lst);
      //TODO new value
    } else if (rawType == AttributeType.ENUM_DEFINITION) {
      JSONArray lst = new JSONArray();
      EnumDefinitionValue edv = (EnumDefinitionValue) rawValue;
      if (edv == null) return;

      attrObj.put("elements_type", adapt(edv.getType()));
      for (EnumValue ev : edv.getValues()) {
        JSONObject jo = enumValueToJSON(adapt(edv.getType()), ev);
        if (jo != null) lst.put(jo);
      }
      attrObj.put("value", lst);
      //TODO new value
    } else {
      if (rawValue == null) return;
      attrObj.put("value", rawValue);
    }
  }

  private static JSONObject enumValueToJSON(AttributeType at, EnumValue ev) throws JSONException {
    if (ev == null) return null;
    JSONObject jo = new JSONObject();
    if (ev.getComment() != null) jo.put("comment", ev.getComment());
    valueToJSON(jo, at, at, ev.getValue());
    return jo;
  }

  public static JSONObject toJSON(UUID uid, Map<String, Attribute> map) throws JSONException {
    List<Attribute> coll = new LinkedList<Attribute>();
    for (String s : map.keySet())
      if (map.get(s) != null)
        //for(Attribute a:map.get(s))
        coll.add(map.get(s));
    return toJSON(uid, coll);
  }
  public static UUID getUUID(JSONObject obj) throws JSONException {
    return UUID.fromString(obj.getString("uuid"));
  }
  public static UUID getUUID(String nodeString) throws JSONException {
    if (nodeString == null) {
        return null;
    }
    try(LoadedJSONObject obj = new LoadedJSONObject(nodeString)){
      return getUUID(obj);
    }
  }

  public static UUID getUUID(InputStream nodeString) throws JSONException {
    if (nodeString == null) {
        return null;
    }
    try(LoadedJSONObject obj = new LoadedJSONObject(nodeString)){
      return getUUID(obj);
    }
  }

  public static UUID tryStringUUID(String cont) {
    int pos = cont.lastIndexOf("\"uuid\"");
    if(pos>-1) {
      pos = cont.indexOf("\"", pos+7);
      int posend = cont.indexOf("\"", pos+1);
      String uid = cont.substring(pos+1, posend);
      if(uid.length()==36)
        return UUID.fromString(uid);
    }
    return null;
  }

}
