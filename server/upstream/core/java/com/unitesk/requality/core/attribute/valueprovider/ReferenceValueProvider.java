package com.unitesk.requality.core.attribute.valueprovider;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.dslplatform.json.JsonReader;
import com.unitesk.jsonclone.JSONArray;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.reference.IReferenceVersionRule;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.model.TreeNode;

public class ReferenceValueProvider implements IValueProvider {
  public static final String ERROR_CHAR = "*";
  public static final String MISSING_MESSAGE = ERROR_CHAR+"Missing({key}): '{val}'";
  public static final String NULL_MESSAGE =  ERROR_CHAR+"Null value";
  public static final String STORED_VERSIONS = "storedVersions";
  private TypedValue raw;
  private Map<String, Map<UUID, String>> loadedVmap = null;
  private Map<String, UUID[]> loadedValToNodes = null;
  public static String TYPE="ReferenceValueProvider";
  public TypedValue readValue(AttributeType at, AttributeType lt, JsonReader reader) throws IOException {
    byte c = reader.last();
    switch(at){
      case LIST:{
        if(c =='}')
          return new TypedValue(AttributeType.LIST, new ListValue(lt, new LinkedList<>()));//empty value
        if(c!='[')
          throw new IOException("List value needs to be started with [");
        if(lt == null)
          lt = AttributeType.STRING;
        if(reader.getNextToken() == ']')
          return new TypedValue(at, new ListValue(lt, new LinkedList<>()));
        List<Object> le = new LinkedList<>();
        le.add(readValue(lt, null, reader).getValue());
        reader.getNextToken();
        while(reader.last() == ','){
          reader.getNextToken();
          le.add(readValue(lt, null, reader).getValue());
          reader.getNextToken();
        }
        if(reader.last()!=']')
            throw new IOException("List value needs to end with ]");
        return new TypedValue(AttributeType.LIST, new ListValue(lt, le));
      }
      default:{
        if(c == '"')//old
        {
          return new TypedValue(AttributeType.STRING, reader.readString());
        }
        if(reader.last()!='{')
          throw new IOException("reference value parsing error!");
        c = reader.getNextToken();
        if(c =='}')
          throw new IOException("reference value parsing error - empty val!");
        String val = null, nod = null, ver = null;
        while(c != '}'){
          if(c == ',')
            c = reader.getNextToken();
          if(c != '"')
            throw new IOException("reference name parsing error");
          String k = reader.readString();
          c = reader.getNextToken();
          if(c != ':')
            throw new IOException("reference name parsing error - no :");
          c = reader.getNextToken();
          if(c != '"')
            throw new IOException("reference value parsing error");
          String vala = reader.readString();
          c = reader.getNextToken();
          if(k.equals("value"))
            val = vala;
          if(k.equals("node"))
            nod = vala;
          if(k.equals("version"))
            ver = vala;
        }
        if(nod!=null){
          if(loadedVmap == null)
            loadedVmap = new HashMap<>();
          if(!loadedVmap.containsKey(val))
            loadedVmap.put(val, new HashMap<>());
          UUID nodu = UUID.fromString(nod);
          loadedVmap.get(val).put(nodu, ver);
          if(loadedValToNodes == null)
            loadedValToNodes = new HashMap<>();
          UUID[]st = loadedValToNodes.get(val);
          if(st == null){
            st = new UUID[1];
            loadedValToNodes.put(val, st);
          }else{
            st = Arrays.copyOf(st, st.length+1);
            loadedValToNodes.put(val, st);
          }
          st[st.length-1] = nodu;
        }
        if(reader.last()!='}')
          throw new IOException("List value needs to end with }");
        return new TypedValue(at, val);
        //String s = reader.readString();
      }
    }
  }
  public ReferenceValueProvider(AttributeType at, AttributeType lt, JsonReader reader){
    //main loading way
    try {
      if(at == AttributeType.LIST && lt == null)
        lt = AttributeType.REFERENCE;
      raw = readValue(at, lt, reader);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  public ReferenceValueProvider(TypedValue tv) {
    raw = tv;
  }
  public ReferenceValueProvider(AttributeType at, Object val) {
    this(new TypedValue(at, val));
  }
  public ReferenceValueProvider(String val) {
    this(val, null, null);
  }
  public ReferenceValueProvider(String val, String nde, String vers) {
    raw = new TypedValue(AttributeType.REFERENCE, val);
    if(nde!=null && !nde.isEmpty()){
        registerLink(val, nde, vers);
    }
  }
  public ReferenceValueProvider(List<ReferenceRawValue> rdesc) {
    List<Object> ret = new LinkedList<>();
    ListValue lv = new ListValue(AttributeType.REFERENCE, ret);
    for(ReferenceRawValue rv:rdesc){
      String val = rv.value;
      ret.add(val);
      String nde = rv.node;
      if(nde!=null)
        registerLink(val,nde,rv.version);
    }
    raw = new TypedValue(AttributeType.LIST, lv);
  }
  private void registerLink(String val, String nde, String vers) {
    if(loadedVmap == null)
      loadedVmap = new HashMap<>();
    if(!loadedVmap.containsKey(val))
      loadedVmap.put(val, new HashMap<>());
    if(nde!=null && !nde.isEmpty()) 
      if(loadedValToNodes == null) {
        loadedValToNodes = new HashMap<>();
        UUID[]st = loadedValToNodes.get(val);
        if(st == null){
          st = new UUID[1];
          loadedValToNodes.put(val, st);
        }else{
          st = Arrays.copyOf(st, st.length+1);
          loadedValToNodes.put(val, st);
        }
        UUID nodu = UUID.fromString(nde);
        loadedVmap.get(val).put(nodu, vers);
        st[st.length-1] = nodu;
      }
    
  }
//  public ReferenceValueProvider(AttributeType at, Object val) {
//    this(new TypedValue(at, val));
//  }
//  public ReferenceValueProvider(TypedValue tv, Map<UUID, String> meta, Map<String, UUID> rawresmap) {
//    this(tv);
////    vmap = meta;
//    //this.rawresmap = rawresmap;
//  }
//  public ReferenceValueProvider(AttributeType at,Object val, Map<UUID, String> meta, Map<String, UUID> rawresmap) {
//    this(new TypedValue(at, val),meta, rawresmap);
//  }

  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    if(raw == null)
      return null;
    if(key == null)
      throw new IllegalArgumentException("Name argument cannot be null for reference value provider!");
    boolean initVers = false;
    IReferenceVersionRule rvr = null;
    boolean wasempty = loadedValToNodes == null;
    if(as!=null){//correct, cases when there is not tree, for ex. version converters
      rvr = as.getNode().getTreeDB().getAttributeRules().getReferenceVersionRule();
      if(rvr != null){//==null - function is not inited
        if(rvr.checkFrom(as.getNode(), key))
        {
          initVers = true;
          if(wasempty){
            loadedValToNodes = new HashMap<>();
            loadedVmap = new HashMap<>();
          }
        }
      }
    }
    if(raw.getType()!=AttributeType.LIST){
      if(raw.getGValue() instanceof ReferenceValue)
        throw new RuntimeException("Ref value is where it does not supposed to be");
      ReferenceValue rv = null;
      String raww = raw.getGValue();
      if(loadedValToNodes!=null){
        rv = ReferenceValue.getOne(as!=null?as.getNode():null, raww,key, loadedValToNodes.get(raww) ,loadedVmap.get(raww));
      }else
        rv = ReferenceValue.getOne(as!=null?as:null,raw.getGValue(),key, rvr);
      rv.initProviderLink(this, initVers && wasempty);
      if(rv.getWarns()!=null){
        handleWarnings(as, rv.getWarns());
      }
//        if(rawresmap!=null){
//          nodes = rawresmap.get(raw.getGValue());
//          //if(nodes!=null)
//            //vers = vmap.get(node);
//        }
      return new TypedValue(AttributeType.REFERENCE, rv);
    }
    else{
      ListValue lv = raw.getGValue();
      List<Object> v = new LinkedList<>();
      if(lv.getType() == AttributeType.STRING ||lv.getType() == AttributeType.REFERENCE)
        for(Object o:lv.getValues()){
          ReferenceValue rv = null;
          String os = o.toString();
          if(loadedValToNodes!=null && loadedValToNodes.containsKey(o)){
            rv = ReferenceValue.getOne(as == null?null:as.getNode(), o,key, loadedValToNodes.get(o), loadedVmap.get(o));
          }else
            rv = ReferenceValue.getOne(as!=null?as:null,o,key, rvr);
          v.add(rv);
          rv.initProviderLink(this, initVers && wasempty);
          if(rv.getWarns()!=null){
            handleWarnings(as, rv.getWarns());
          }
        }
      else
        if(lv.getType() == AttributeType.LIST)
          for(Object o:lv.getValues())
            if(o instanceof ListValue){
              ListValue lva = (ListValue)o;
              if(lva.getType() == AttributeType.LIST || lva.getType() == AttributeType.REFERENCE)
                v.add(handleListValue(lva,as!=null?as.getNode():null,key, rvr));
            }
      return new TypedValue(AttributeType.LIST, new ListValue(AttributeType.REFERENCE, v));
    }
//    List<TreeNode> res = lr.resolveLink(lr, as.getNode(), key, null, key, true, true);
//    if(res.size()>0){
//      List<Object> ret = new ArrayList<>();
//      for(TreeNode node:res)
//        ret.add(node.getSUUId());
//      return new TypedValue(AttributeType.LIST,new ListValue(AttributeType.REFERENCE, ret));
//    }
//    return new TypedValue(AttributeType.REFERENCE, res.isEmpty()?null:res.iterator().next().getSUUId());
  }

  public void handleWarnings(AttributeStorage as, CopyOnWriteArrayList<IMark> warns) {
    TreeNode nde = as.getNode();
    if(localwarns!=null){
      nde.getTreeDB().removeWarns(nde, localwarns.toArray(new IMark[0]));
    }
    nde.getTreeDB().addWarnings(as.getNode(), warns);
    if(warns!=null && !warns.isEmpty())
    {
      if(localwarns == null)
        localwarns = new LinkedList<>();
      localwarns.addAll(warns);
    }
  }
  private Object handleListValue(ListValue lv, TreeNode node, String key, IReferenceVersionRule rvr) {
    List<Object> v = new LinkedList<>();
    if(lv.getType() == AttributeType.REFERENCE)
      for(Object o:lv.getValues()){
        if(loadedValToNodes!=null && loadedValToNodes.containsKey(o)){
          v.add(new TypedValue(AttributeType.REFERENCE, ReferenceValue.getOne(node, o, key,loadedValToNodes.get(o), loadedVmap.get(o))));
        }else{
          v.add(new TypedValue(AttributeType.REFERENCE, ReferenceValue.getOne(node!=null?node.attrs():null,o, key, rvr)));
        }
      }
    else
      if(lv.getType() == AttributeType.LIST)
        for(Object o:lv.getValues()){
          if(o instanceof ListValue){
            ListValue lva = (ListValue)o;
            if(lva.getType() == AttributeType.LIST || lva.getType() == AttributeType.REFERENCE)
              v.add(handleListValue(lva, node, key, rvr));
          }
        }
    return v;
  }

  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return NULLDEP;
  }


  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public TypedValue getRawValue() {
    return raw;
  }

  @Override
  public Map<String, Object> getRawStores() {
    return null;
  }
  
  @Override
  public void attach(TreeNode node) {
  }

  List<IMark> localwarns = null;
  @Override
  public void detach(TreeNode node) {
    if(localwarns!=null)
      node.getTreeDB().removeWarns(node, localwarns.toArray(new IMark[0]));
  }

  @Override
  public boolean equals(Object obj) {
    if(!(obj instanceof ReferenceValueProvider))
      return false;
    if(!((ReferenceValueProvider)obj).raw.equals(raw))
      ((ReferenceValueProvider)obj).raw.equals(raw);
    return ((ReferenceValueProvider)obj).raw.equals(raw);
  }
  @Override
  public void saveJSON(JSONObject vpo) throws JSONException {
    JSONArray jo = new JSONArray();
    if(raw.getType() == AttributeType.LIST)
      vpo.put("atype", AttributeType.LIST.toString());
    vpo.put("value", toJson(jo, raw.getType(), raw.getValue()));
    //NodeMapper.saveValue(vpo, raw);
  }
  private Object toJson(JSONArray jo, AttributeType at, Object val) throws JSONException {
    if(at == AttributeType.LIST)
    {
      JSONArray ja = new JSONArray();
      ListValue lv = (ListValue) val;
      if(lv.getValues().size() == 0)
        return null;
      for(Object o : lv.getValues()){
        Object t = toJson(ja, lv.getType(), o);
        if(t != null)
          ja.put(t);
      }
      return ja;
    }
    if(val!=null){
      String s = val.toString();
      JSONObject ja = new JSONObject();
      ja.put("value", s);
      if(s!=null && loadedValToNodes!=null){
        UUID[] u = loadedValToNodes.get(s);
        //TODO list
        if(u!=null){
          ja.put("node", u[0].toString());
          if(loadedVmap.containsKey(s)){
            String va = loadedVmap.get(s).get(u[0]);
            if(va!=null){
              ja.put("version", va);
            }
          }
        }
      }
      return ja;
    }
    return null;
  }

  @Override
  public AttributeType getAttributeType() {
    return raw.getType();
  }
  public void handleRVResolving( AttributeStorage as, 
      String raw2, UUID[] nodes, Map<UUID, String> storedNodeVers, CopyOnWriteArrayList<IMark> warns, boolean persist) {
    if(persist){
      if(nodes!=null){
        if(loadedValToNodes == null)
          loadedValToNodes = new HashMap<>();
        loadedValToNodes.put(raw2, nodes);
      }
      if(storedNodeVers!=null){
        if(loadedVmap == null)
          loadedVmap = new HashMap<>();
        loadedVmap.put(raw2, storedNodeVers);
      }
    }
    handleWarnings(as, warns);
  }

//    if(stored == null){
//      vmap = null;
//      return;
//    }
//    vmap = new HashMap<>();
//    LinkedList<TypedValue> ll = (LinkedList<TypedValue>) stored.get(STORED_VERSIONS);
//    Iterator<TypedValue> it = ll.iterator();
//    while(it.hasNext()){
//      TypedValue tv1 = it.next(), tv2 = it.next();
//      vmap.put(tv1.getGValue(), tv2.getGValue());
//    }
}
