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
package com.unitesk.requality.core.attribute;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.unitesk.jsonclone.JSONArray;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.valueprovider.ValueProviderFactory;
import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.SortingSettings.SortingMethod;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class Attribute implements Comparable<Attribute> {
  public static final String ENUM_CHAR = "\u24BA";
  private static Set<String> statusNames = new HashSet<>(), hiddenReplace = new HashSet<>();

  private final TypedValue value;
  //private final Version version;
  private final AttributeDefinition adef;


  //Names of attributes to be replaced in raw value

  static {
    registerHTMLNames(TreeNode.ATTR_DESCRIPTION);
    registerStatusName(TreeNode.ATTR_TYPE);
  }
  public static Set<String> getStatusNames(){
    return statusNames;
  }
  public static Set<String> getHTMLNames(){
    return hiddenReplace;
  }
  public static void registerStatusName(String name) {
    statusNames.add(name);
  }

  public static void registerHTMLNames(String name) {
    hiddenReplace.add(name);
  }

  public boolean isVirtual(){
    return adef.isVirtual();
  }

  public static boolean affectsPosition(TreeNode node, String attr) {
    if (VirtualNode.ATTR_COLLAPSEVNODE.equals(attr)) return true;
    if (VirtualNode.ATTR_HIDDEN_STATUS.equals(attr)) return true;
    SortingMethod sm = node.getTreeDB().getSortingSettings().getMethod();
    if (sm == SortingMethod.BY_INDEX
        || sm == SortingMethod.BY_LOCATIONS
            && node.getTreeDB().getTreeLogic().usesIndexByDefault(node.getType()))
      if(attr == TreeDB.INDEX_ATTRIBUTE)
        return true;
    if (sm == SortingMethod.BY_NAMES) return attr.equals(TreeNode.ATTR_LABEL);
    return attr.equals(Requirement.ATTR_LOCATIONS) || attr.equals(TreeNode.ATTR_LABEL);
  }

  public AttributeType getType(){ 
    TypedValue tv = getTValue();
    if(tv == null)
      return AttributeType.STRING;//Default //TODO change?
    return tv.getType();
    //return adef.getType();
  }

  /***
   * Method used in calculations of node's general attributes version.
   * 
   */
  @Override
  public int hashCode() {
    int v = adef.hashCode();
    if(value!=null)
      v ^=  value.hashCode();
//    int v = origin.hashCode() ^ key.hashCode();
//    if (type != null) v = v ^ type.toString().hashCode();
//    if (version != null) v = v ^ Long.hashCode(version.getSInt());
//    else v = v ^ Long.hashCode(-1L);
//    if (generator != null) v = v ^ generator.hashCode(); // TODO version?
//    Object rawValue = getRawValue();
//    if (rawValue != null) v = v ^ rawValue.hashCode();
//    if (getListType() != null) v = v ^ getListType().toString().hashCode();
//    return v ^ getAvailability().toString().hashCode();
    return v;
  }

//  public Version getVersion() {
//    return version;
//  }

  public AttributeAvailability getAvailability() {
    return adef.getAvailability();
  }

  public boolean isMoreVisible(TreeDB db, Attribute a, UUID where, boolean samemore) {
    if (a == null) return true;
    if (where.equals(getOrigin())) {
      if (!where.equals(a.getOrigin())) return true;
    } else {
      if (where.equals(a.getOrigin())) return false;
    }
    if (adef.isGenerated()) {
      if (a.adef.isGenerated()) {
      } else return false;
    } else if (a.adef.isGenerated()) {
      return true;
    }
    return this.adef.isMoreAVVisibleThan(a.adef)
        || (this.adef.isSameAVVisibleWith(a.adef)&&this.isMasterMoreVisibleThan(
            db, a, this == a && samemore)); // )
  }
  // assume that all the rest are updated
 
  public Attribute(AttributeDefinition ad, AttributeStorage as){
    this.adef = ad;
    this.value = ad.getTValue(as);
  }

  public String getKey() {
    return adef.getKey();
  }
  
  public TypedValue getTValue(){
    //if(value == null)
      //value = adef.getValue(as);
    return value;
  }
  public <T> T getGValue(){
   // if(value == null)
     // value = adef.getValue(as);
    if(value == null)
      return null;
    return (T) value.getValue();
  }

  private static List<Object> toList(ListValue o) {
    // TODO Auto-generated method stub
    List<Object> ret = new ArrayList<>();
    for (Object i : o.getValues())
      if (o.getType() == AttributeType.LIST) ret.add(toList((ListValue) i));
      else ret.add(i);
    return ret;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    sb.append(getAvailability() == AttributeAvailability.LOCAL
              ? "L"
              : getAvailability() == AttributeAvailability.DIRECT_CHILDREN ? "D" : "S");
    sb.append("]'");
    sb.append(getKey());
    sb.append("'");
    sb.append(":=");
    sb.append(value != null ? value.toString() : "null");
    sb.append("(");
    sb.append(super.toString());
    sb.append(")");
    //sb.append(getGenerator() == null ? "none" : getGenerator().toString());
    return sb.toString();
  }

  public static boolean equalsValues(Object value2, Object object) {
    if (value2 instanceof ListValue && object instanceof ListValue) {
      ListValue o1 = (ListValue) value2, o2 = (ListValue) object;
      if (o1.getValues().size() == o2.getValues().size() && o1.getType() == o2.getType()) {
        return Arrays.equals(o1.getValues().toArray(), o2.getValues().toArray());
      } else return false;
    }
    if (value2 instanceof AppliedEnumValue && object instanceof AppliedEnumValue) {
      AppliedEnumValue aev1 = (AppliedEnumValue) value2, aev2 = (AppliedEnumValue) object;
      return equalsValues(aev2.getValue(), aev1.getValue())
          && equalsValues(aev2.getComment(), aev1.getComment())
          && equalsValues(aev2.getBaseKey(), aev1.getBaseKey());
    }
    return value2 == null && object == null || value2 != null && value2.equals(object);
  }

  static final Pattern valid_inner_name_pattern =
      Pattern.compile("[_]{0,2}[%]{0,1}[A-Za-z0-9а-яА-Я_[$].,-]+[A-Za-z0-9а-яА-Я,._[$] -]*");
  static final Pattern valid_name_pattern =
      Pattern.compile("[A-Za-z0-9а-яА-Я,.-]+[A-Za-z0-9а-яА-Я,._[$] -]*");
  public static final String ATTR_READ_ONLY_LIST = "_readOnlyAttrs";

  public static boolean checkNameIsValid(String name, boolean inner) {
    if (name == null) return false;
    return inner
        ? valid_inner_name_pattern.matcher(name).matches()
        : valid_name_pattern.matcher(name).matches();
  }

  @Override
  public int compareTo(Attribute o) {
    return this.getKey().compareTo(o.getKey());
  }
//  protected Attribute clone(AttributeStorage as) throws CloneNotSupportedException {
//    return new Attribute(adef, as);
//  }

  public UUID getOrigin() {
    return adef.getOrigin();
  }

  private boolean isMasterMoreVisibleThan(TreeDB db, Attribute a, boolean samemore) {
    if (getOrigin() == null || a.getOrigin() == null) return true;
    if (getOrigin().equals(a.getOrigin())) return samemore; // TODO check
    AttributeStorage as1 = AttributeStorage.getCachedStorage(db, getOrigin()),
        as2 = AttributeStorage.getCachedStorage(db, a.getOrigin());
    if (as1 != null && as2 != null) {
      String s1 = as1.getNode().getQualifiedId(), s2 = as2.getNode().getQualifiedId();
      int q1 = as1.getNode().getQualifiedId().length(),
          q2 = as2.getNode().getQualifiedId().length();
      if (q1 > q2) return true;
      return s1.equals(s2) ? samemore : false;
    }
    return false; // false for eqs by def
  }
  public boolean isList(){
    return getType() == AttributeType.LIST;
  }
  public static String valueToString(AttributeStorage as, AttributeType at, Object value) {
    StringBuilder sb = new StringBuilder();
    if(at == AttributeType.STRING && value!=null)
      return value.toString();
    if (!checkValueByType(at, value)) 
      return "INCORRECT!";
    EnumValue ev = null;
    if (value instanceof EnumValue) {
      ev = (EnumValue) value;
      value = ev.getValue();
    }
    if (at == AttributeType.MAP) {
      if(value instanceof MapValue) {
        MapValue mv = (MapValue)value;
        AttributeType at2 = mv.getType();
        sb.append(at2);
        sb.append("={");
        boolean was = false;
        for(Entry<String, Object> s:mv.getValues().entrySet()) {
        if(was)
          sb.append(",");
        sb.append(s.getKey());
        sb.append(":");
        sb.append(valueToString(as, at2, s.getValue()));
        was = true;
        }
        sb.append("}");
      } else {
        Map<String, Object> mv = (Map<String, Object>)value;//TODO
        sb.append("{");
        boolean was = false;
        if(mv!=null)
          for(Entry<String, Object> s:mv.entrySet()) {
            if(was)
              sb.append(",");
            sb.append(s.getKey());
            sb.append(":");
            sb.append(valueToString(as, AttributeType.STRING, s.getValue()));
           was = true;
          } 
        sb.append("}");        
      }
    }
    if (at == AttributeType.LIST) {
      ListValue lv = (ListValue) value;
      AttributeType at2 = lv == null ? AttributeType.STRING: lv.getType();
      sb.append(at2);
      sb.append("=[");
      if(lv!=null) {
        Iterator<Object> it = lv.iterator();
        while (it.hasNext()) {
          sb.append(valueToString(as, at2, it.next()));
          if (it.hasNext()) sb.append(",");
        }
      }
      sb.append("]");
    } else if (at == AttributeType.ENUM_DEFINITION) {
      EnumDefinitionValue lv = (EnumDefinitionValue) value;
      AttributeType at2 = lv == null? AttributeType.STRING: lv.getType();
      sb.append(at2);
      sb.append("=[");
      if(lv!=null) {
        Iterator<EnumValue> it = lv.iterator();
        while (it.hasNext()) {
          sb.append(valueToString(as, at2, it.next().getValue()));
          if (it.hasNext()) sb.append(",");
        }
      }
      sb.append("]");
    } else if (value != null){
      if(value instanceof IContextStringProvider)
        sb.append(((IContextStringProvider)value).toString(as));
      else
        sb.append(value.toString());
    }
    else sb.append("NULL");
    if (ev != null) {
      if (ev.getComment() != null) {
        sb.append("{");
        sb.append(ev.getComment());
        sb.append("}");
      }
    }
    return sb.toString();
  }
  public static boolean checkValueByType(AttributeType at, Object value2) {
    if (at == AttributeType.ENUM_DEFINITION) return value2 instanceof EnumDefinitionValue;
    if (at == AttributeType.ENUM) return value2 instanceof AppliedEnumValue;
    if (at == AttributeType.LIST) return value2 instanceof ListValue;
    if (at == AttributeType.BOOL) return value2 instanceof Boolean || value2 instanceof String;
    if (at == AttributeType.INT) return value2 instanceof Integer;
    if (at == AttributeType.REFERENCE) return value2 instanceof ReferenceValue || value2 instanceof String;
    if (at == AttributeType.DATE) return value2 instanceof String; // TODO long?
    if (at == AttributeType.LIST) return checkListValue((ListValue) value2);
    if (at == AttributeType.STRING) return value2 instanceof String;
    // TODO additional checks
    return true;
  }
  private static ITreeNodeMapper defMap = new ITreeNodeMapper() {
    @Override
    public Object getResult(TreeNode node) {
      return node.getUserVisibleName();
    }
  }; 
  public static Object getJavaValue(TreeNode node, Attribute a) {
    return getJavaValue(node, a.getType(),a.getValue(), defMap);
  }
  public static Object getJavaValue(TreeNode node, AttributeType at, Object val, ITreeNodeMapper tnm) {
    switch(at){
      case BOOL:
      case STRING:
      case INT:
      case FLOAT:
      case HTML:
      case DATE:
        return val;
      case ENUM:
        return ((AppliedEnumValue)val).getValue();
      case ENUM_DEFINITION:{
        EnumDefinitionValue edv = (EnumDefinitionValue)val;
        return getJavaListE(node, edv.getType(), edv.getValues(), tnm);
      }
      case LIST:{
        ListValue lv = (ListValue)val;
        return getJavaList(node, lv.getType(),lv.getValues(), tnm);
      }
      case REFERENCE:{
        List<Object> ret = new ArrayList<>();
        ReferenceValue rv = (ReferenceValue) val;
        if(rv.hasErrorsOnResolve(node.attrs()))
          return rv.getStringValue();
        else
          for(TreeNode nde:rv.getNodes(node.attrs()))
            if(nde!=null)
              ret.add(tnm.getResult(nde));
        return ret;
      }
      case RESOURCE:{
        throw new RuntimeException("Not implemented");
      }

      case TREENODE:{
        if(val instanceof UUID){
          UUID uid = (UUID)val;
          TreeNode lf = node.getTreeDB().getNode(uid);
          if(lf!=null)
            return tnm.getResult(lf);
          return null;
        }
        if(val == null)
          return null;
        if(val instanceof TreeNode)
          return tnm.getResult((TreeNode)val);
        if(val instanceof Iterable){
          Iterable<TreeNode> it = (Iterable<TreeNode>) val;
          Iterator<TreeNode> ite = it.iterator();
          List<Object> ret = new ArrayList<>();
          while(ite.hasNext())
            ret.add(tnm.getResult(ite.next()));
          return ret;
        }
        throw new RuntimeException("Not implemented");
      }
    }
    return null;
  }
  private static Object getJavaList(TreeNode node, AttributeType at, List<Object> values, ITreeNodeMapper tnm) {
    List<Object> evs = new ArrayList<>();
    for(Object ev:values)
      evs.add(getJavaValue(node, at, ev, tnm));
    return evs;
  }
  private static Object getJavaListE(TreeNode node, AttributeType at, List<EnumValue> values, ITreeNodeMapper tnm) {
    List<Object> evs = new ArrayList<>();
    for(EnumValue ev:values)
      evs.add(getJavaValue(node, at, ev.getValue(), tnm));
    return evs;
  }
  public static Object rawValueToJSON(AttributeStorage as, AttributeType at, Object value, String string, String string2) {
    StringBuffer sb2 = new StringBuffer();
    if (value instanceof EnumValue) {
      EnumValue ev = (EnumValue) value;
      value = ev.getValue();
    }
    if(at == AttributeType.REFERENCE && (value instanceof ReferenceValue))//for new value only
    {
      JSONObject jo = new JSONObject();
      ReferenceValue rv = ((ReferenceValue)value);
      try {
      jo.put("value", rv.toString(as));
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
      if(rv.getRaw()!=null)
        try {
      jo.put("raw", rv.getRaw());
      } catch (JSONException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
      }
      JSONArray ja = new JSONArray();
      if(rv.getStoredNodes()!=null)
        for(UUID uid:rv.getStoredNodes())
          ja.put(uid.toString());
      try {
      jo.put("nodes", ja);
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
      return jo;
    }else
    if (at == AttributeType.LIST) {
      ListValue lv = (ListValue) value;
      AttributeType at2 = lv.getType();
      JSONArray joa = new JSONArray();
      // sb2.append("[");
      Iterator<Object> it = lv.iterator();
      while (it.hasNext()) {
        joa.put(rawValueToJSON(as, at2, it.next(), string, string2));
      }
      return joa;
      // sb2.append("]");
    } else if (at == AttributeType.ENUM_DEFINITION) {
      EnumDefinitionValue lv = (EnumDefinitionValue) value;
      AttributeType at2 = lv.getType();
      JSONArray joa = new JSONArray();
      Iterator<EnumValue> it = lv.iterator();
      while (it.hasNext()) {
      joa.put(rawValueToJSON(as, at2, it.next().getValue(),string, string2));
      }
      return joa;
    } else {
      if (value != null) {
        if(value instanceof String && string !=null) {
          String ret = ((String)value).replace(ResManager.RES_VAR, string).replace(CoreUtils.URLEncode(ResManager.RES_VAR), string);
          if(string2!=null)
            return ret.replace(ResManager.ATTR_RES_VAR, string2).replace(CoreUtils.URLEncode(ResManager.ATTR_RES_VAR), string2);
          return ret;
        }else
          return value;
      } else sb2.append("NULL");
    }
    return value;
  }

  public static Object resultValueToJSON(AttributeType at, Object value, URI uri, URI keyurl) {
    if (at == AttributeType.LIST || at == AttributeType.ENUM_DEFINITION) {
      ArrayList<Object> lv = (ArrayList<Object>) value;
      JSONArray joa = new JSONArray();
      Iterator<Object> it = lv.iterator();
      while (it.hasNext()) {
        Object o = it.next();
        if (o instanceof List) joa.put(resultValueToJSON(at, o, uri,keyurl));
        else {
          joa.put(o.toString().replace(ResManager.RES_VAR, uri.toString()).replace(ResManager.ATTR_RES_VAR, keyurl.toString()));
        }
      }
      return joa;
    } else if (at == AttributeType.ENUM) {
      return ((EnumValue) value).getValue().toString().replace(ResManager.RES_VAR, uri.toString()).replace(ResManager.ATTR_RES_VAR, keyurl.toString());
    } else {
      if (value != null) return (value.toString().replace(ResManager.RES_VAR, uri.toString()).replace(ResManager.ATTR_RES_VAR, keyurl.toString()));
      else return ("NULL");
    }
  }

  /**
   * * Converts value of attribute type to another type
   *
   * @param from old type
   * @param to new type
   * @param newVal value to convert
   * @return new value if possible or null otherwise
   */
  public static Object convertValue(
      TreeNode node, AttributeType from, AttributeType to, Object newVal, String enumName) {
    if (from == to) return newVal;
    AttributeStorage as = node!=null?node.attrs():null;
    if (from == AttributeType.LIST && to == ((ListValue)newVal).getType())
    {
      ListValue lv = (ListValue) newVal;
      if(lv.getValues().size()==0)
        return AttributeType.getDefaultValue(to);
      if(lv.getValues().size() == 1){
        newVal = lv.getValues().iterator().next();
        if(newVal instanceof ReferenceValue)
          newVal = ((ReferenceValue)newVal).getRaw();
        return newVal;
      }
    }
    if (to == AttributeType.STRING){
      if(newVal instanceof IContextStringProvider)
        return ((IContextStringProvider)newVal).toString(as);
      return newVal.toString();
    }
    String newS = valueToString(as,from, newVal);
    if (from == AttributeType.LIST || from == AttributeType.ENUM_DEFINITION) newS = newS.replace("[", "").replace("]", "");
    if (to == AttributeType.BOOL) {
      try {
        return Boolean.parseBoolean(newS.trim().toLowerCase());
      } catch (Exception e) {
        RLogger.logDebug(CoreUtils.getTrace(e));
        return false;
      }
    } else if (to == AttributeType.FLOAT) {
      try {
        return Double.parseDouble(newS.trim());
      } catch (Exception e) {
        RLogger.logDebug(CoreUtils.getTrace(e));
        return 0.0d;
      }
    } else if (to == AttributeType.INT) {
      try {
        return Integer.parseInt(newS.trim());
      } catch (Exception e) {
        RLogger.logDebug(CoreUtils.getTrace(e));
        return 0;
      }
    } else if (to == AttributeType.REFERENCE || to == AttributeType.DATE) {
      return newS;
    } else if (to == AttributeType.ENUM && enumName != null) {
      if(node == null)
        throw new UnsupportedOperationException("Enum conversion cannot be performed without node");
      Attribute edef = node.getTreeDB().getRootNode().attrs().getAttribute(enumName);
      TypedValue edf = edef.getTValue();
      if (edf != null&&edf.getType()==AttributeType.ENUM_DEFINITION){
        EnumDefinitionValue edv = edf.getGValue();
        if(edv!=null)
          for (EnumValue o : edv.getValues())
            if (o.getValue().toString().equals(newS)) return o.createApplied(edv.getType(), enumName);
        RLogger.logDebug(
            "Value "+(newS!=null?newS:"null")+" was not found in "+enumName+"!");
      }
    } else if (to == AttributeType.LIST) {
      List<Object> list = new ArrayList<>();
      if(newVal instanceof ReferenceValue)
        list.add(((ReferenceValue)newVal).getRaw());
      else
        list.add(newVal);
      return new ListValue(from, list);
    }
    throw new RuntimeException(
        "Unsopported operation - conversion from " + from + " to " + to + "!");
    // return null;
  }

  public static String[] getBuiltinTypes() {
    AttributeType[] ats = AttributeType.visibleValues();
    String[] ret = new String[ats.length - 1];
    for (int i = 0; i < ret.length; i++) ret[i] = ats[i].toString();
    return ret;
  }

  public static String[] getAvailableTypes(TreeDB db, IAttributeFilter filter) {
    if(db.isVirtual())
      db = Instantiator.getRootTree(db);
    TreeNode node = db.getRootNode();
    Set<String> names = new HashSet<>();
    for (Attribute a : node.attrs()) {
      // TODO was filter so needed?
      
      if (a.getType().equals(AttributeType.ENUM_DEFINITION)){
        TypedValue tv = a.getTValue();
        if(filter.filter(a)
          && Attribute.checkValueByType(AttributeType.ENUM_DEFINITION, tv.getValue())
          && !((EnumDefinitionValue) tv.getValue()).getValues().isEmpty())
            names.add(a.getKey());
      }
    }
    String[] types = getBuiltinTypes();
    String[] ret = new String[types.length + names.size()];
    int i = 0;
    for (String at : types) ret[i++] = at;

    for (String s : names) ret[i++] = ENUM_CHAR + s;
    i = ret.length - 1;
    while (ret[i] == null) i--;
    return Arrays.copyOf(ret, i + 1);
  }



  public static String[] getAttributeValues(TreeDB db, String name) {

    DeepFirstTreeWalker walker = new DeepFirstTreeWalker(db.getRootNode(), false, true, false);
    Iterator<TreeNode> it = walker.iterator();
    Set<String> vals = new HashSet<>();
    while (it.hasNext()) {
      TreeNode node = it.next();
      Attribute a = node.attrs().getAttribute(name);
      if (a != null){
        TypedValue tv = a.getTValue();
        if(tv.getValue()!=null &&
          tv.getType() != AttributeType.LIST && 
          tv.getType() != AttributeType.RESOURCE &&
          a.getType() != AttributeType.ENUM_DEFINITION) {
            vals.add(
                tv.getType().name() + ": " + tv.getValue().toString());
        }
       
      }
    }
    return vals.toArray(new String[0]);
  }

  public boolean isLocal(UUID uid) {
    if (uid == null) return true;
    UUID or = getOrigin();
    return or == null || or.equals(uid);
  }

  public boolean isReference() {
    TypedValue tv = getTValue();
    if(tv == null)
      return false;
    return tv.getType().equals(AttributeType.REFERENCE)
        || tv.getType().equals(AttributeType.LIST) && 
            tv.getValue()!=null && 
            ((ListValue)tv.getValue()).getType().equals(AttributeType.REFERENCE);
  }
  public boolean isCP() {
    TypedValue tv = getTValue();
    if(tv == null)
      return false;
    return tv.getType().equals(AttributeType.REFERENCE) && 
        tv.getValue()!=null && 
        ((tv.getValue() instanceof ReferenceValue)&&((ReferenceValue)tv.getValue()).getRaw().contains(ReferenceValue.CP_SEPARATOR))
        || tv.getType().equals(AttributeType.LIST) && 
            tv.getValue()!=null && 
            ((ListValue)tv.getValue()).getType().equals(AttributeType.REFERENCE)
            &&cpInList((ListValue)tv.getValue());
  }
  private boolean cpInList(ListValue value2) {
    for(Object o:value2) 
      if(o!=null){
        if(o instanceof ReferenceValue) {
          if(((ReferenceValue)o).getRaw().contains(ReferenceValue.CP_SEPARATOR))
            return true;
        }else
          if(o.toString().contains(ReferenceValue.CP_SEPARATOR))
            return true;
      }
    return false;
  }
  public boolean strongEquals(Attribute attr) {
//    if (!attr.getVersion().equals(getVersion())) return false;
    return equals(attr);
  }


  private static boolean checkListValue(ListValue lv) {
    for (Object o : lv.getValues()) if (!checkValueByType(lv.getType(), o)) return false;
    return true;
  }
//
//  public void setVersion(Integer newv) {
//    version.setInt(newv);
//  }

  public AttributeDefinition getDefinition() {
    return adef;
  }
  public Object getValue() {
    TypedValue tv = getTValue();
    if(tv == null)
      return null;
    return tv.getValue();
  }
  public AttributeType getListType() {
    TypedValue tv = getTValue();
    if(tv.getType()==AttributeType.LIST){
      ListValue lv = tv.getGValue();
      if(lv!=null)
        return lv.getType();
    }
    if(tv.getType()==AttributeType.ENUM_DEFINITION){
      EnumDefinitionValue edv = tv.getGValue();
      if(edv!=null)
        return edv.getType();
    }
    return null;
  }
  public boolean isUserVisible() {
    return (getDefinition().getFlags()&Flag.HIDDEN)!=Flag.HIDDEN;
  }
  public Object getJavaValue(TreeNode node) {
    return getJavaValue(node, getType(), getValue(), defMap);
  }
  public static TypedValue getTypedValue(Object object) {
    if(object instanceof TypedValue)
      return (TypedValue)object;
    return new TypedValue(AttributeType.detectType(object), object);
  }
  public boolean isReadOnly(TreeDB db) {
    if((getDefinition().getFlags()&Flag.READ_ONLY)==1)
      return true;
   return isReadOnly(db,getKey());
  }
  public static boolean isReadOnly(TreeDB db, String key) {
    List<Object> ro =  db.getRootNode().attrs().getListAttributeValue(ATTR_READ_ONLY_LIST);
    if(ro!=null && !ro.isEmpty())
      return ro.contains(key);
    return false;
  }
  public static boolean isNonCopyable(TreeDB db, String key, boolean rootInCopyableTree) {
    if(ValueProviderFactory.isVirtName(null,key))
      return true;
    if(rootInCopyableTree&&key.equals(TreeDB.INDEX_ATTRIBUTE))
      return true;
    if(key.equals("_nextid"+FIDUpdater.getFIDName(db)))
      return true;
    if(key.equals("_"+FIDUpdater.getFIDName(db)))
      return true;
    if(key.equals(FIDUpdater.getFIDName(db)))
      return true;
    List<Object> ro =  db.getRootNode().attrs().getListAttributeValue(ATTR_READ_ONLY_LIST);
    if(ro!=null && !ro.isEmpty())
      return ro.contains(key);
    return false;
  }
  public static String getValidName(String s) {
    if(checkNameIsValid(s, false))
      return s;
    StringBuilder sb = new StringBuilder();
    for(int i = 0;i<s.length();i++){
      char c = s.charAt(i);
      if(valid_name_pattern.matcher(Character.toString(c)).matches()){//TODO make faster
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
