/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.core.storage;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.dslplatform.json.BoolConverter;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonReader;
import com.dslplatform.json.NumberConverter;
import com.unitesk.jsonclone.JSONArray;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AppliedEnumValue;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.IRelatedFilesProvider;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.VPFactory;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.CoreUtils;

public class NodeMapper {
  private static int readNumber(JsonReader reader) throws IOException {
    return NumberConverter.deserializeInt(reader);
//    if(reader.last() !='-'&&!Character.isDigit(reader.last()))
//      reader.getNextToken();
//    //TODO fload
//    byte min = 0;
//    int ret = 0;
//    if(reader.last() == '-'){
//      min = 1;
//      reader.getNextToken();
//    }
//    while(Character.isDigit(reader.last())){
//      ret = reader.last()-'0'+ret*10;
//      reader.getNextToken();
//    }
//    return min == 0?ret:-1*ret;
  }
  private static double readDouble(JsonReader reader) throws IOException {
    return NumberConverter.deserializeDouble(reader);
//    if(reader.last() !='-'&&!Character.isDigit(reader.last()))
//      reader.getNextToken();
//    //TODO fload
//    byte min = 0;
//    int ret = 0;
//    if(reader.last() == '-'){
//      min = 1;
//      reader.getNextToken();
//    }
//    while(Character.isDigit(reader.last())){
//      ret = reader.last()-'0'+ret*10;
//      reader.getNextToken();
//    }
//    return min == 0?ret:-1*ret;
  }
  private static double readDoubleNumber(JsonReader reader) throws IOException {
    return NumberConverter.deserializeDouble(reader);
//    if(reader.last() !='-'&&!Character.isDigit(reader.last()))
//      throw new IOException("incorrect number!");
//    //TODO fload
//    byte min = 0;
//    double ret = 0;
//    if(tp.pos == '-'){
//      min = 1;
//      tp.pos = reader.getNextToken();
//    }
//    while(Character.isDigit(tp.pos)){
//      ret = tp.pos-'0'+ret*10;
//      tp.pos = reader.getNextToken();
//    }
//    StringBuilder sb = new StringBuilder();
//    if(tp.pos=='.'){
//      double db = 0.1;
//      tp.pos = reader.getNextToken();
//      while(Character.isDigit(tp.pos)){
//        ret += (tp.pos-'0')*db;
//        db = db/10;
//      }
//    
//    }
//    return min == 0?ret:-1*ret;
  }
  public static class MarkableFileInputStream extends FilterInputStream {
    private FileChannel myFileChannel;
    private long mark = -1;

    public MarkableFileInputStream(FileInputStream fis) {
        super(fis);
        myFileChannel = fis.getChannel();
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void mark(int readlimit) {
        try {
            mark = myFileChannel.position();
        } catch (IOException ex) {
            mark = -1;
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        if (mark == -1) {
            throw new IOException("not marked");
        }
        myFileChannel.position(mark);
    }
}
  private static Map<String, AttributeDefinition> getDIRECTAttributes(UUID uid, FileInputStream fileInputStream, IRelatedFilesProvider resFolder) throws JSONException, IOException {
    return getAttributes(uid, fileInputStream, resFolder, true, Method.DIRECT);
  } 
  private static Map<String, AttributeDefinition> getOBJECTAttributes(UUID uid, FileInputStream fileInputStream, IRelatedFilesProvider resFolder) throws JSONException, IOException {
    return getAttributes(uid, fileInputStream, resFolder, true, Method.OBJECT);
  }
  private NodeMapper() {}
 
  public static Object readAny(UUID uid, JsonReader reader) throws IOException {
    switch(reader.last()){
      case '"':
        return reader.readString();
      case '[':{
        if(reader.getNextToken()==']')
          return new LinkedList<>();
        Object a = null;
        List<Object> ret = new LinkedList<>();
        ret.add(readAny(uid,reader));
        //TODO int
        while(reader.getNextToken()==','){
          reader.getNextToken();
          ret.add(readAny(uid,reader));
        }
        reader.checkArrayEnd();
        return ret;
      }
      case '{':
        return readTP(uid,AttributeType.STRING, reader);//TODO test
      default:
        if(Character.isDigit(reader.last()) || reader.last()=='-')
          return readNumber(reader);//TODO float nums
    }
    return null;
  }
  static UUID readJsonUUID(JsonReader reader) throws IOException {
    int deep = 0;
    boolean inquote = false;
    boolean ignorenext = false;
    reader.getNextToken();
    do{
      if(ignorenext){
        ignorenext = false;
      }else
	      switch(reader.last()){
	  	    case '\\': ignorenext = true; break;
	        case '"': inquote = !inquote; break;
	        case '{': if(!inquote) deep++; break;
	        case '}': if(!inquote)deep--; break;
	      }
      if(deep == 1 && reader.last() == '"'){
    	  inquote = !inquote;
          String name = reader.readKey();
          if(name.toLowerCase().equals("uuid"))
            return UUID.fromString(reader.readString());
          if(reader.last() == '{')
            deep++;
        }
	  reader.getNextToken();
    } while(deep>0);
    return null;
  }
  private static Map<String,AttributeDefinition> readAttrDefs(UUID uid, JsonReader reader, IRelatedFilesProvider resFolder) throws IOException{
    String aname, key;
    AttributeType at;
    int flag;
    String aparam, origin;
    AttributeAvailability aa;
    IValueProvider provider;
    Map<String,AttributeDefinition> ret = Collections.emptyMap();
    int deep = 0;
    byte stage = 0;
    aname = null;
    key = null;
    at = AttributeType.STRING;
    flag = 0;
    aparam = null;
    origin = null;
    aa = AttributeAvailability.LOCAL;
    provider = null;
    //TokenPos tp = new TokenPos((byte) 0);
    if(reader.getNextToken()!='"')
      throw new IOException("wrong format");
    String sectname = reader.readKey();
    if(!sectname.equals("attributes"))
      throw new RuntimeException("attr defs gathering requires proper section");
    if(reader.last()!='{')
      throw new RuntimeException("attr defs gathering - mailformed structure - no {");
    if(reader.getNextToken()=='}')
      throw new RuntimeException("attr defs gathering - emtpy attrs");
    boolean first = true;
    do{
      if(first)
        first = false;
      else
        reader.getNextToken();
      aname = key = reader.readKey();
      if(reader.last()!='{')
        throw new RuntimeException("attr defs gathering - mailformed structure - no {");
      if(reader.getNextToken()=='}')
        throw new RuntimeException("attr defs gathering - empty");
      boolean first2 = true;
      do{
        if(first2)
          first2 = false;
        else
          reader.getNextToken();
        aparam = reader.readKey(); 
        switch(aparam){
          case "key":
            if(reader.last()!='"')
              throw new RuntimeException("attr defs gathering - mailformed structure - no \"");
            key = reader.readString();
          break;
          case "origin":
            if(reader.last()!='"')
              throw new RuntimeException("attr defs gathering - mailformed structure - no \"");
            origin = reader.readString();
          break;
          case "availability":
            if(reader.last()!='"')
              throw new RuntimeException("attr defs gathering - mailformed structure - no \"");
            aa = AttributeAvailability.valueOf(reader.readString());
          break;
          case "flag":
            flag = (int)readNumber(reader);
          break;
          case "value":
            provider = readValueProvider(uid,reader, resFolder);
          break;
        }
      }while(reader.getNextToken()==',');
      try {
        reader.checkObjectEnd();
      }catch(Exception e) {
        e.printStackTrace();
      }
      if(aname!=null){
        if(provider == null)
          RLogger.logInfo("No value provider provided "+uid);
        if(ret.isEmpty())
          ret = new TreeMap<>();
        if(provider!=null)
          ret.put(key, new AttributeDefinition(origin==null?uid:UUID.fromString(origin),
                                  key,
                                  aa,
                                  provider,
                                  flag));
        aname = null;
        key = null;
        at = AttributeType.STRING;
        flag = 0;
        aparam = null;
        origin = null;
        aa = AttributeAvailability.LOCAL;
        provider = null;
      }
    }while(reader.getNextToken()==',');
    reader.checkObjectEnd();
//    if(aname!=null)
//      ret.put(key, new AttributeDefinition(origin==null?where:UUID.fromString(origin),
//                                          key,
//                                          aa,
//                                          provider,
//                                          flag));//add final one
    return ret;
  }
  public static IValueProvider readValueProvider(UUID uid, JsonReader reader, IRelatedFilesProvider resFolder) throws IOException{
    if (reader.wasNull()) return null;
    if (reader.last() == '"') {
      return VPFactory.initProvider(uid, ConstValueProvider.TYPE,
          AttributeType.STRING,
          null, null, null, resFolder, reader);
    }
    if (reader.last() != '{') 
      throw new IOException("Expecting '{' for IValueProvider start");
    if (reader.getNextToken() == '}') return null;
    try{
      IValueProvider vp = null;
      AttributeType at = AttributeType.STRING, etype = null;
      String enumKey = null, type = ConstValueProvider.TYPE;
      Map<String, Object> addParams = null;
      boolean first = true;
      do{
        if(first)
          first = false;
        else
          reader.getNextToken();
        String key = reader.readKey();
        switch(key){
          case "type":{
            if(reader.last()!='"')
              throw new IOException("Expecting '\"' for IValueProvider param val");  
            type = reader.readString();
          }break;
          case "atype":{
            if(reader.last()!='"')
              throw new IOException("Expecting '\"' for IValueProvider param val");  
            at = AttributeType.valueOf(reader.readString());
          }break;
          case "enum_key":{
            if(reader.last()!='"')
              throw new IOException("Expecting '\"' for IValueProvider param val");  
            enumKey = reader.readString();
          }break;
          case "elements_type":{
            if(reader.last()!='"')
              throw new IOException("Expecting '\"' for IValueProvider param val");  
            etype = AttributeType.valueOf(reader.readString());
  
          }break;
          case "value":{//needs to be the last one
            vp = VPFactory.initProvider(uid, type, at, etype, enumKey, addParams, resFolder, reader);
            //deep--;//} is end sign for provider
          }break;
          default:{
            if(addParams==null)
              addParams = new TreeMap<>();
            addParams.put(key, readAny(uid,reader));
          }break;
        }
      }while (reader.getNextToken() == ',');
      reader.checkObjectEnd();
      if(vp == null && type!=null)//no value init
        vp = VPFactory.initProvider(uid, type, at, etype, enumKey, addParams, resFolder, reader);
      if (vp == null)
        throw new IOException("Parsing error: No value for IValueProvider atype "+uid);
      return vp;
    }
    catch(IOException e){
      RLogger.logError("error while parsing "+uid+" code is down here");
      RLogger.logError(e);
      throw e;
    }
  }
  public static DslJson<Object> dslJson = new DslJson<>();
  private static long t1 = 0, t2 = 0, last = 0;

  public static UUID getUUIdExc(InputStream nodeString) throws JSONException, IOException {
    if (nodeString == null) {
      return null;
    }
    JsonReader<Object> jr = dslJson.newReader(nodeString, new byte[1000]);
    UUID uy = readJsonUUID(jr);
    return uy;
  }
  public static UUID getUUId(InputStream nodeString) throws JSONException {
    try {
      return getUUIdExc(nodeString);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }finally {
      //TODO add nodeString.close();?
    }
    return null;
  }

  public static Map<String, AttributeDefinition> getAttributes(
      UUID uuid, InputStream nodeString,IRelatedFilesProvider resFolder) throws JSONException, IOException {
    return getAttributes(uuid, nodeString, resFolder, true);
  }
  private enum Method{DIRECT, OBJECT};
  public static Map<String, AttributeDefinition> getAttributes(
      UUID uuid, InputStream nodeString,IRelatedFilesProvider resFolder, boolean yeld) throws JSONException, IOException {
    //Map<String, AttributeDefinition> ad1 = getAttributes(uuid, nodeString, yeld, Method.OLD);
    Map<String, AttributeDefinition> ad2 = getAttributes(uuid, nodeString, resFolder, yeld, Method.DIRECT);
    //showCompareRes(ad1,ad2);
    return ad2;
  }
  private static void showCompareRes(
      Map<String, AttributeDefinition> ad1, Map<String, AttributeDefinition> ad2) {
    if(!ad2.keySet().equals(ad1.keySet()))
        System.out.println("diff keys");
    else
      for(String s:ad1.keySet()){
        AttributeDefinition ada1 = ad1.get(s),
          ada2 = ad2.get(s);
        if(!ada1.equals(ada2))
          System.out.println("diff adefs" );
      }
  }
  public static Map<String, AttributeDefinition> getAttributes(
      UUID uuid, InputStream nodeString, IRelatedFilesProvider resFolder, boolean yeld, Method m) throws JSONException, IOException {
    if (nodeString == null) {
      return null;
    }
    Map<String,AttributeDefinition> ads = null;
    switch (m){
      case DIRECT:{
        JsonReader<Object> jr = dslJson.newReader(nodeString, new byte[1000]);
        if(jr.getNextToken()!='{')
          throw new IOException("json file needs to start with {");
        ads = readAttrDefs(uuid, jr, resFolder);
      }break;
      case OBJECT:{
        JsonReader<Object> jr = dslJson.newReader(nodeString, new byte[1000]);
        //jr.next(JSONNode.class);
        if(jr.getNextToken()!='{')
          throw new IOException("json file needs to start with {");
       // ads = readAttrDefs(uuid, jr);
      }break;
    }

    return ads;
  }

  private static void loadNewFormat(
      UUID uuid, Map<String, AttributeDefinition> result, JSONObject attr, IRelatedFilesProvider resFolder)
      throws JSONException {
    String[] jnames = JSONObject.getNames(attr);
    if (jnames == null) return; //TODO return throw?
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
        IValueProvider vp = jsonToValueProvider(uuid, a.getJSONObject("value"), resFolder);
        rattr = new AttributeDefinition(origin,key,pa,vp,flag);
        result.put(key, rattr);
      }else
        throw new RuntimeException("no value for "+key+" in "+origin+"!" ); 
    }
  }
  public static void saveValue(JSONObject setts, TypedValue tv) throws JSONException{
    saveValue(setts, tv.getType(),tv.getValue());
  }
  public static void saveValue(JSONObject setts, AttributeType at, Object val) throws JSONException{
    if(at!=AttributeType.STRING)
      setts.put("atype", at).toString();
    if(val!=null)
      valueToJSON(setts, at, val, "value");
  }

  public static TypedValue loadValue(JSONObject setts) throws JSONException {
    AttributeType type = AttributeType.STRING;
    if(setts.has("atype"))
      try {
        type = AttributeType.valueOf(setts.getString("atype"));
      } catch (JSONException e) {
        e.printStackTrace();
      }
    Object value = valueFromJSON(type, setts);
    return new TypedValue(type, value);
  }
  private static Object valueFromJSON(AttributeType type, JSONObject a) throws JSONException {
    Object value;
    if (type == AttributeType.BOOL) {
      if (!a.has("value")) return null;
      String v = a.getString("value");
      if ("true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
        value = Boolean.valueOf(v);
      } else {
        value = Boolean.FALSE;
      }
    } else if (type == AttributeType.LIST) {
      value = listFromJSON(a);
    } else if (type == AttributeType.ENUM_DEFINITION) {
      value = enumDefinitionFromJSON(a);
    } else if (type == AttributeType.REFERENCE) {
      if (!a.has("value")) return null;
      value = a.getString("value");
    } else if (type == AttributeType.ENUM) {
      value = appliedEnumFromJSON(a);
    } else {
      if (!a.has("value")) return null;
      // TODO: all attribute types support
      try {
        if (!a.has("value")) value = AttributeType.getDefaultValue(type);
        else value = type.fromString(a.getString("value"));
      } catch (Exception e) {
        value = a.getString("value");
      }
    }
    return value;
  }

  private static AppliedEnumValue appliedEnumFromJSON(JSONObject a) throws JSONException {
    if (!a.has("enum_key")) return null;
    AttributeType at = AttributeType.STRING;
    if(a.has("elements_type"))
        at = AttributeType.valueOf(a.getString("elements_type"));
    EnumValue ev = enumFromJSON(at, a);
    return new AppliedEnumValue(ev.getValue(), ev.getComment(), at, a.getString("enum_key"));
  }

  private static EnumValue enumFromJSON(AttributeType at, JSONObject a) throws JSONException {
    Object val = valueFromJSON(at, a);
    String comm = null;
    if (a.has("comment")) comm = a.getString("comment");
    return new EnumValue(val, comm);
  }

  public static ListValue listFromJSON(JSONObject a) throws JSONException {
    if (!a.has("value")) return null;
    AttributeType ltype = AttributeType.STRING;
    if (a.has("elements_type")) ltype = AttributeType.valueOf(a.getString("elements_type"));
    ArrayList<Object> attrLst = new ArrayList<>();
    JSONArray vals = a.getJSONArray("value");
    for (int j = 0; j < vals.length(); j++) {
      Object o = vals.get(j);
      if (o instanceof JSONObject) attrLst.add(valueFromJSON(ltype, vals.getJSONObject(j)));
      else attrLst.add(o); //backward comp
    }
    return new ListValue(ltype, attrLst);
  }

  private static EnumDefinitionValue enumDefinitionFromJSON(JSONObject a) throws JSONException {
    if (!a.has("value")) return null;
    JSONArray vals = a.getJSONArray("value");

    AttributeType ltype = AttributeType.STRING;
    if (a.has("elements_type")) ltype = AttributeType.valueOf(a.getString("elements_type"));

    EnumDefinitionValue edv = new EnumDefinitionValue(ltype, new ArrayList<EnumValue>());

    for (int j = 0; j < vals.length(); j++)
      edv.addEnumValue(enumFromJSON(ltype, vals.getJSONObject(j)));

    //        if(ltype != AttributeType.LIST&&ltype != AttributeType.ENUM_DEFINITION)
    //        	for (int j = 0; j < vals.length(); j++) {
    //        		attrLst.put(ltype.fromString(vals.getString(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j));
    //        	}
    //        else
    //        {
    //        	if(ltype == AttributeType.LIST)
    //        		for (int j = 0; j < vals.length(); j++) {
    //        			attrLst.put(listFromJSON(vals.getJSONObject(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j));
    //        		}
    //
    //        	if(ltype == AttributeType.ENUM_DEFINITION)
    //        		for (int j = 0; j < vals.length(); j++) {
    //        			attrLst.put(enumDefinitionFromJSON(vals.getJSONObject(j)),valsComments==null?null:valsComments.getString(j).equals("")?null:valsComments.getString(j));
    //        		}
    //        }

    return edv;
  }

  public static final JSONObject toJSON(TreeNode node, boolean ignoreVirtual) throws JSONException {
    return toJSON(node.getUUId(), AttributeStorage.toDefMap(node.attrs().getLMap()).values(),ignoreVirtual); //TODO
  }

  public static final JSONObject toJSON(UUID uuid, Collection<AttributeDefinition> collection,boolean ignoreVirtual)
      throws JSONException {
    return toJSON(uuid, collection, false,ignoreVirtual);
  }

  public static final JSONObject toJSON(UUID uuid, Collection<AttributeDefinition> collection, boolean kdup, boolean ignoreVirtual)
      throws JSONException {
    //if(collection.isEmpty())
      //throw new RuntimeException("Empty attributes list on "+uuid+"!");
    JSONObject result = null;
    result = new JSONObject();
    result.put("uuid", uuid);
    JSONObject attrs = new JSONObject();
    boolean handled = false;
    for (AttributeDefinition a : collection) {
      if (ignoreVirtual && (a.isVirtual()||a.getKey().equals(TreeNode.ATTR_ID)))
        continue;
      if (!a.getOrigin().equals(uuid) && !a.isHidden()){
        RLogger.logDebug("Attempt to save not local attr on "+uuid+" that is "+a.getOrigin(), 5);
        continue;
      }
      handled = true;
      //if (a.getKey().startsWith("_%id")) continue; //ignore virt attr
      JSONObject attrObj = new JSONObject();
      //attrObj.put("key", a.getKey());
      //if (a.getType() != AttributeType.STRING) attrObj.put("type", a.getType());
      if (!a.getOrigin().equals(uuid)) attrObj.put("origin", a.getOrigin());
      //attrObj.put("version", a.getVersion().toString());
      //if (a.getRawValue() instanceof ArrayList) RLogger.logInfo("problem!");
      //Object val = a.getGValue();
      IValueProvider vp = a.getValueProvider();
      valueProviderToJSON(attrObj,a,vp);
      if(a.getSavedFlags()!=0)
        attrObj.put("flag", a.getSavedFlags());
      //TODO make valueProvider-specific save
      //valueToJSON(attrObj, a.getType(), a.getType(), vp.getRawValue());
      if (!a.getAvailability().equals(AttributeAvailability.LOCAL))
        attrObj.put("availability", a.getAvailability().name());
      
//      AttributeGenerator ag = null;
//      if(vp instanceof GeneratorValueProvider)
//        ag = ((GeneratorValueProvider)vp).getGenerator();
//      if (ag != null) {
//        JSONObject genObj = new JSONObject();
//        genObj.put("uuid", ag.getUUID());
//        genObj.put("attribute_type", ag.getAttributeType().ordinal());
//        if (ag.getAttributeListType() != null)
//          genObj.put("atribute_list_type", ag.getAttributeListType().ordinal());
//        genObj.put("generator_type", ag.getGeneratorType().getId());
//        JSONObject genAttrs = new JSONObject();
//        Map<String, Object> setts = ag.getSettings();
//        for (String s : setts.keySet())
//          if (setts.get(s) instanceof ListValue) {
//            JSONObject val = new JSONObject();
//            valueToJSON(val, AttributeType.LIST, AttributeType.LIST, (ListValue) setts.get(s));
//            genAttrs.put(s, val);
//          } else genAttrs.put(s, setts.get(s));
//        genObj.put("settings", genAttrs);
//        attrObj.put("generator", genObj);
//      }
      attrs.put(kdup ? a.getKey() : (a.getKey()), attrObj);
    }
    if(!handled)
      throw new RuntimeException("Empty attributes set for "+uuid+" "+Arrays.toString(collection.toArray()));
    result.put("attributes", attrs);
    return result;
  }
  private static IValueProvider jsonToValueProvider(
      UUID or, JSONObject attrObj, IRelatedFilesProvider resFolder) throws JSONException {
    //JSONObject vpo = new JSONObject();
    //attrObj.put("valueProvider", vpo);
    String vt = ConstValueProvider.TYPE;
    if(attrObj.has("type")){
      JSONObject tp = attrObj.optJSONObject("type");
      if(tp != null)
        vt = tp.getString("value");
      else
        vt = attrObj.getString("type");
    }
    //if(attrObj.has("atype"))
      //  at = AttributeType.valueOf(attrObj.getString("atype"));
    ////Object o = valueFromJSON(at, attrObj);
    //Map<String, Object> setts = new HashMap<>();
    //JSONObject set = new JSONObject();
//    if(attrObj.has("settings"))
//      set = attrObj.getJSONObject("settings");
////    JSONArray names = set.names();
//    if (names != null)
//      for (int i = 0; i < names.length(); i++) {
//        try {
//          JSONObject jo = set.getJSONObject(names.getString(i));
//          setts.put(names.getString(i), listFromJSON(jo));
//        } catch (JSONException e) {
//          setts.put(names.getString(i), set.get(names.getString(i)));
//        }
//      }
    return VPFactory.initProvider(vt, attrObj, resFolder);
  }
  private static void valueProviderToJSON(
      JSONObject attrObj, AttributeDefinition a, IValueProvider vp) throws JSONException {
    JSONObject vpo = new JSONObject();
    attrObj.put("value", vpo);
//    TypedValue tv = vp.getRawValue();
//    if(tv!=null){
//      //if(tv.getType()!=AttributeType.STRING)
//        //vpo.put("atype", tv.getType());//TODO mb remove?
//      valueToJSON(vpo, tv.getType(), tv.getValue(),"value");
//    }
//    Map<String,Object> setts = vp.getRawStores();
//    JSONObject genAttrs = new JSONObject();
//    if(setts!=null&&!setts.isEmpty()){
//      for (String s : setts.keySet())
//        if (setts.get(s) instanceof ListValue) {
//          JSONObject val = new JSONObject();
//          valueToJSON(val, AttributeType.LIST, setts.get(s),"value");
//          genAttrs.put(s, val);
//        } else genAttrs.put(s, setts.get(s));
//      vpo.put("settings", genAttrs);
//    }
    if(vp == null) {
      RLogger.logError("Null valueProvider during node loading");
      RLogger.logError(CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
      return;
    }
    vp.saveJSON(vpo);
    if(!vp.getType().equals(ConstValueProvider.TYPE))
      vpo.put("type", vp.getType());
  }

  public static void valueToJSON(
      JSONObject attrObj, AttributeType type, Object rawValue,String key)
      throws JSONException {
    if (type.equals(AttributeType.ENUM)) {
      AppliedEnumValue aev = (AppliedEnumValue) rawValue;
      if (aev == null) return;
      if(aev.getType()!=AttributeType.STRING)
        attrObj.put("elements_type", aev.getType());
      attrObj.put("enum_key", aev.getBaseKey());
      String c = aev.getComment();
      if(c!=null && !c.isEmpty())
      attrObj.put("comment", c);
      valueToJSON(attrObj, aev.getType(), aev.getValue(),key);
      // 	        if(rawValue!=null)
      // 	        	attrObj.put("rawvalue",aev.toStored());
    } else if (type == AttributeType.LIST) {
      JSONArray lst = new JSONArray();
      ListValue lv = (ListValue) rawValue;
      if (rawValue == null) return;

      //Object[]val = (Object[])rawValue;
      AttributeType at = lv.getType();
      if(at!=AttributeType.STRING)
        attrObj.put("elements_type", at);
      for (Object o : lv) {
        if(o == null){
          RLogger.logError("Exception - tried to save null in list value");
          RLogger.logError(CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
          continue;
        }
        JSONObject jso = new JSONObject();
        valueToJSON(jso, at, o,key);
        lst.put(jso);
      }
      attrObj.put(key, lst);
      //TODO new value
    } else if (type == AttributeType.ENUM_DEFINITION) {
      JSONArray lst = new JSONArray();
      EnumDefinitionValue edv = (EnumDefinitionValue) rawValue;
      if (edv == null) return;
      if(edv.getType()!=AttributeType.STRING)
        attrObj.put("elements_type", edv.getType());
      for (EnumValue ev : edv.getValues()) {
          if(ev == null){
              RLogger.logError("Exception - tried to save null in list value");
              RLogger.logError(CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
              continue;
            }
        JSONObject jo = enumValueToJSON(edv.getType(), ev);
        if (jo != null) lst.put(jo);
      }
      attrObj.put(key, lst);
      //TODO new value
    }else if (type == AttributeType.REFERENCE && rawValue instanceof ReferenceValue) {
        ReferenceValue edv = (ReferenceValue) rawValue;
        attrObj.put(key, edv.getRaw().toString());
      //TODO new value
    } else {
      
      if (rawValue == null) return;
//      if(node!=null&&(rawValue instanceof String))
//        rawValue = rawValue.toString().replace("{node.resURL}", node);
        
      attrObj.put(key, rawValue);
    }
  }

  private static JSONObject enumValueToJSON(AttributeType at, EnumValue ev) throws JSONException {
    if (ev == null) return null;
    JSONObject jo = new JSONObject();
    if (ev.getComment() != null) jo.put("comment", ev.getComment());
    valueToJSON(jo, at, ev.getValue(),"value");
    return jo;
  }

  public static JSONObject toJSON(UUID uid, Map<String, AttributeDefinition> map,boolean ignoreVirtual) throws JSONException {
    List<AttributeDefinition> coll = new LinkedList<>();
    for (String s : map.keySet())
      if (map.get(s) != null)
        //			for(Attribute a:map.get(s))
        coll.add(map.get(s));
    return toJSON(uid, coll, ignoreVirtual);
  }


	public static Map<String, TypedValue> convertToMap(JSONObject set) {
	  Map<String, TypedValue> setts = new HashMap<>();
	  JSONArray names = set.names();
	  if (names != null)
	    for (int i = 0; i < names.length(); i++) {
	      try {
	        JSONObject jo = set.getJSONObject(names.getString(i));
	        setts.put(names.getString(i), new TypedValue(AttributeType.LIST,listFromJSON(jo)));
	      } catch (JSONException e) {
	        try {
				setts.put(names.getString(i), Attribute.getTypedValue(set.get(names.getString(i))));
			} catch (JSONException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
	      }
	    }
	  return setts;
	}

  private static Charset UTF8 = Charset.forName("UTF-8");
  public static UUID getUUIdExc(String file) throws JSONException, IOException {
    if(file==null)
      return null;
    file = file.trim();
    if (file.endsWith("}")) {
      int len = file.length();
      String temp = file.substring(len - 48);
      if (temp.startsWith("uuid\"")) return UUID.fromString(temp.substring(8, 44));
      if (temp.startsWith("\"uuid\"")) return UUID.fromString(temp.substring(9, 45));
      temp = temp.substring(1);
      if (temp.startsWith("\"uuid\"")) return UUID.fromString(temp.substring(9, 44));
    }
    ByteArrayInputStream bais = new ByteArrayInputStream(file.getBytes(UTF8));
    try{
      return getUUIdExc(bais);
    }finally{
      try {
        bais.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
  public static UUID getUUId(String file) throws JSONException {
    try {
      return getUUIdExc(file);
    } catch (IOException e) {
      RLogger.logError(e);
    }
    return null;
  }
  //TODO mb not needed
  public static TypedValue readTP(UUID uid, AttributeType at, JsonReader reader) throws IOException{
    if(reader.last() == ']')
      return null;
    if(reader.last()!='{')
      throw new IOException("Mailformed typed value - needs to start with { ("+reader.last()+")"+uid);
    if(reader.getNextToken()=='}')
      throw new IOException("Mailformed typed value - empty "+uid);
    AttributeType elat = null;
    String ek = null, com = null;
    Object val = null;
    boolean first = true;
    Map<String, Object> addParams = null;
    do{
      if(first)
        first = false;
      else
        reader.getNextToken();
      String paramName = reader.readKey();
      switch (paramName) {
        case "atype":
          if(reader.last()!='"')
            throw new IOException("Mailformed typed value - attrs value not started with \"");
          at = AttributeType.valueOf(reader.readString());
        break;
        case "elements_type":
          if(reader.last()!='"')
            throw new IOException("Mailformed typed value - attrs value not started with \"");
          elat = AttributeType.valueOf(reader.readString());
          break;
        case "enum_key":
          if(reader.last()!='"')
            throw new IOException("Mailformed typed value - attrs value not started with \"");
          ek = reader.readString();
          break;
        case "comment":
          if(reader.last()!='"')
            throw new IOException("Mailformed typed value - attrs value not started with \"");
          com = reader.readString();
          break;
        case "value":
          val = readTPValue(uid, at, elat, ek, addParams, reader);
          break;
        default:
          if(addParams == null)
            addParams = new TreeMap<>();
          addParams.put(paramName, readAny(uid, reader));
          break;
      }
    }while(reader.getNextToken()==',');
    reader.checkObjectEnd();
    return new TypedValue(at, val);
  }

  public static Object readTPValue(UUID uid, AttributeType at, AttributeType lt, String enumKey, Map<String, Object> addParams, JsonReader reader) throws IOException {
    switch(at){
      case FLOAT:
        return readDoubleNumber(reader);//TODO test
      case BOOL:{
        if(reader.last()=='"')
          return Boolean.parseBoolean(reader.readString());
        else
          return BoolConverter.deserialize(reader);
//        if(!Character.isLetter(tp.pos))
//          tp.pos = reader.getNextToken();
//        StringBuilder sb = new StringBuilder();
//        while(Character.isLetter(tp.pos)){
//          sb.append((char)tp.pos);
//          tp.pos = reader.getNextToken();
//        }
//        return Boolean.parseBoolean(sb.toString());
      }
      case INT:
        if(reader.last()=='"')
        {
          RLogger.logInfo("Int number is stored as string for node "+uid);
          return Integer.parseInt(reader.readString());
        }
        return readNumber(reader);//TODO test
      case ENUM:{
        if(reader.last()!='"')
          throw new IOException("Enum value needs to be started with \"");
        return new AppliedEnumValue(reader.readString(), addParams!=null?(String)addParams.get("comment"):null, 
          addParams!=null?AttributeType.valueOf((String)addParams.get("elements_type")):lt!=null?lt:AttributeType.STRING, enumKey);
      }  //return readNumber(tp, reader);//TODO test
      case LIST:{
        char last = (char)reader.last();
        if(last!='[')
          if(last == '}') {
            //empty with no value
            return new ListValue(lt, new LinkedList<>());
          }else
            throw new IOException(uid+": List value needs to be started with [");
        if(lt == null)
          lt = AttributeType.STRING;
        if(reader.getNextToken() == ']')
          return new ListValue(lt, new LinkedList<>());
        List<Object> le = new LinkedList<>();
        le.add(readTP(uid,lt, reader).getValue());
        while(reader.getNextToken() == ','){
          reader.getNextToken();
          le.add(readTP(uid, lt, reader).getValue());
        }
        reader.checkArrayEnd();
        return new ListValue(lt, le);
      }
      case ENUM_DEFINITION:{
        if(reader.last()!='[')
          throw new IOException("Enum def value needs to be started with [");
        if(lt == null)
          lt = AttributeType.STRING;
        if(reader.getNextToken() == ']')
          return new EnumDefinitionValue(lt, new LinkedList<>());
        List<EnumValue> le = new LinkedList<>();
        le.add(readEnumValue(uid, lt, reader));
        while(reader.getNextToken()==','){
          reader.getNextToken();
          le.add(readEnumValue(uid, lt, reader));
        }
        reader.checkArrayEnd();
       //Object tva = readTPValue(lt, null, reader);
        return new EnumDefinitionValue(lt, le);
        //dslJson.deserializeList(reader);
      }
      case HTML:{
        if(reader.last()!='"')
          throw new RuntimeException("Incorrect value for html type");
        String s = reader.readString();
        return s;
      }
      default:{
//        if(tp.pos!='"')
  //        throw new RuntimeException("wrong read of string");
        if(reader.last()!='"')
          return readAny(uid, reader);//change because it can be not string
        else {
          String s = reader.readString();
          return s;
        }
      }
    }
//    byte b = reader.getNextToken();
//    if(b == '"')
//      return reader.readString();
   // return null;
//    byte b = 0;{
//      
//      
//    }while(b);
//    if(setts.has("atype"))
//      try {
//        type = AttributeType.valueOf(setts.getString("atype"));
//      } catch (JSONException e) {
//        e.printStackTrace();
//      }
//    Object value = valueFromJSON(type, setts);
//    return new TypedValue(type, value);
  }
  private static EnumValue readEnumValue(UUID uid, AttributeType lt, JsonReader reader) throws IOException {
    if(reader.last()!='{')
      throw new IOException("mailformed enum value definition - no {");
    if(reader.getNextToken()=='}')
      throw new IOException("empty enum value definition");
    String comm = null;
    EnumValue val = null;
    Map<String, Object> addParams = null;
    AttributeType elat = null;
    String ek = null;
    boolean first = true;
    do{
      if(first)
        first = false;
      else
        reader.getNextToken();
      String key = reader.readKey();
      switch(key){
        case "comment":
          if(reader.last()!='"')
            throw new IOException("mailformed enum value param - param value have no \"");
          comm = reader.readString();
          break;
        case "elements_type":
          if(reader.last()!='"')
            throw new IOException("mailformed enum value param - param value have no \"");
          elat = AttributeType.valueOf(reader.readString());
          break;
        case "enum_key":
          if(reader.last()!='"')
            throw new IOException("mailformed enum value param - param value have no \"");
          ek = reader.readString();
          break;
        case "value":
          val = new EnumValue(readTPValue(uid, lt, elat, ek, addParams, reader), comm);//TODO list in enums
          break;
        default:
        {
          if(addParams == null)
            addParams = new TreeMap<>();
          addParams.put(key, readAny(uid, reader));
        }
      }
    }while(reader.getNextToken()==',');
    reader.checkObjectEnd();
    return val;
  }

}
