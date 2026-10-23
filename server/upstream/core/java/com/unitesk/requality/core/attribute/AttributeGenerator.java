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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.nodeiterator.AttributeValueIteratorType;
import com.unitesk.requality.core.nodeiterator.IAttributeGenerator;
import com.unitesk.requality.core.nodeiterator.RequalityIterator;
import com.unitesk.requality.core.nodeiterator.RequalityIteratorType;
import com.unitesk.requality.core.storage.NodeMapper;

public class AttributeGenerator extends RequalityIterator
    implements IAttributeGenerator, java.lang.Comparable<RequalityIterator> {
  public static final String SETT_ITERTYPE = "_iteratorType",
      SETT_AVAILABILITY = "_availability",
      SETT_FORMULA = "_formula",
      SETT_ITERATION = "_iter",
      SETT_TYPE = "generator_type",
      SETT_MIN = "_min",
      SETT_MAX = "_max",
      RAND_VALUES = "_randValues";
  private Map<String, TypedValue> settings;
  private static Map<String, IAttributeGenerator> genMap =
      new HashMap<>();

  public static void initGenMap(Map<String, IAttributeGenerator> p) {
    genMap = p;
  }

  static {
    /*
     * Activator a = Activator.getDefault(); if (a != null) {
     * genMap.putAll(a.loadAttributeGeneratorsDescriptions()); }
     */
    for (String s : genMap.keySet()) {
      IAttributeGenerator gen = genMap.get(s);
      AttributeValueIteratorType.registerAVIType(s, gen.getLabel());
    }
  }

  private Object getSettsValue(AttributeType at, String as, Object def) {
    if (!settings.containsKey(as)) return def;
    TypedValue tv = settings.get(as);
    if(tv.getType()!=at)
      return Attribute.convertValue(null, tv.getType(), at, tv.getValue(), null);
    return tv.getValue();
  }

  public static boolean iteratorTypeIsHidden(AttributeValueIteratorType nitt) {
    return false;
  }

  //private AttributeValueIteratorType ittype = AttributeValueIteratorType.fromId("CYCLE");

  // for Cycle id iterator and random
  // private int min, max, iter;

  private Random rand = null;

  // for Set type and for current set of Random Vars
  // private List<String> setValues;

  public AttributeGenerator(UUID guid, Map<String, TypedValue> settings2) {
    super(
        guid,
        RequalityIteratorType.ATTRIBUTE);
    //this.baseAttribute = relatedAttr;
    loadSettings(settings2);
  }

  public AttributeGenerator(UUID guid, JSONObject filterMap) {
	this(guid, NodeMapper.convertToMap(filterMap));
  }

@Override
  public boolean equals(Object obj) {
    if (super.equals(obj)) return true;
    if (!(obj instanceof AttributeGenerator)) {
      return false;
    }
    // AttributeGenerator it = (AttributeGenerator) obj;
    return super.equals(obj);
  }

  private List<Object> generateSet(int count) {
    List<Object> setValues = new LinkedList<>();
    for (int i = 0; i < count; i++)
      setValues.add(
          Integer.toString((getRand().nextInt(Math.abs(getMax() - getMin()) + 1) + getMin())));
    return setValues;
    // updateMaster();
  }

  private Random getRand() {
    if (rand != null) return rand;
    rand = new Random();
    return rand;
  }

  public AttributeType getAttributeListType() {
    if (getGenType().getId().equals(AttributeValueIteratorType.CYCLE_ID)) return AttributeType.STRING;
    else if (getGenType().getId().equals(AttributeValueIteratorType.RANDOM_ID))
      return AttributeType.STRING;
    else if (getGenType().getId().equals(AttributeValueIteratorType.FORMULA_ID)) return null;
    else if (genMap.containsKey(getGenType().getId()))
      return genMap.get(getGenType().getId()).getAttributeListType();
    return AttributeType.STRING;
  }

  private AttributeValueIteratorType getGenType() {
    TypedValue tv = settings.get(SETT_TYPE);
    if(tv == null)
      return null;
    String type = tv.getGValue();
    if(type == null)
      return null;
    return AttributeValueIteratorType.fromId(type);
  }

  public AttributeType getAttributeType() {
    if (getGenType().getId().equals(AttributeValueIteratorType.CYCLE_ID)) return AttributeType.LIST;
    else if (getGenType().getId().equals(AttributeValueIteratorType.RANDOM_ID)) return AttributeType.LIST;
    else if (getGenType().getId().equals(AttributeValueIteratorType.FORMULA_ID))
      return AttributeType.STRING;
    else if (genMap.containsKey(getGenType().getId()))
      return genMap.get(getGenType().getId()).getAttributeType();
    return AttributeType.STRING;
    // return AttributeType.STRING;
  }
  //
  // public Set<String> getDependencies(TreeNode treeNode) {
  // updateDependencies(treeNode);
  // if (dependencies == null || !dependencies.containsKey(treeNode))
  // return new HashSet<String>();
  // return dependencies.get(treeNode);
  // }

  public void setGeneratorType(AttributeValueIteratorType type) {
    settings.put(SETT_TYPE, new TypedValue(AttributeType.STRING, type.getId()));
  }

  public AttributeValueIteratorType getGeneratorType() {
    return getGenType();
  }

  public int getItemsCount() {
    if (getGenType() == null || getGenType().getId() == null) return 0;
    if (getGenType().getId().equals(AttributeValueIteratorType.CYCLE_ID)) {
      return getIteration() != 0 ? ((getMax() - getMin()) / getIteration()) + 1 : 0;
    } else if (getGenType().getId().equals(AttributeValueIteratorType.RANDOM_ID)) {
      return getIteration();
    } else if (getGenType().getId().equals(AttributeValueIteratorType.FORMULA_ID)) {
      return 1;
    } else if (genMap.containsKey(getGenType().getId()))
      return genMap.get(getGenType().getId()).getItemsCount();
    return 0;
  }

  public int getIteration() {
    return (Integer) getSettsValue(AttributeType.INT, SETT_ITERATION, 0);
  }

  @Override
  public String getLabel() {
    return getGenType().getLabel();
  }

  public int getMax() {
    return (Integer) getSettsValue(AttributeType.INT,SETT_MAX, 0);
  }

  public int getMin() {
    return (Integer) getSettsValue(AttributeType.INT,SETT_MIN, 0);
  }

  public String getPredicate() {
    return (String) getSettsValue(AttributeType.STRING, SETT_FORMULA, "");
  }
  public Object getSingleValue(AttributeStorage targ, int i2, Set<String> deps) {
    // In a case of new variable it will be inactive
    Object ret = "";
    if (getGenType().getId().equals(AttributeValueIteratorType.CYCLE_ID)) {
      ret = i2 * getIteration() + getMin();
    } else if (getGenType().getId().equals(AttributeValueIteratorType.RANDOM_ID)) {
      return (getRandValues(false).getValues()).get(i2);
    } else if (getGenType().getId().equals(AttributeValueIteratorType.FORMULA_ID)) {
      try {
        if(deps==null)
          deps = AttributeStorage.formPredDeps(getPredicate(), targ);
        ret = targ.evaluatePredicateString(getPredicate(),deps);
      } catch (Exception e) {
        String s = e.getMessage();
        String tor = "";
        if (s != null)
          for (int k = 0; k < s.length(); k++)
            if (Character.isJavaIdentifierPart(s.charAt(k)) || Character.isWhitespace(k))
              tor += (s.charAt(k));
        ret = tor;
      }
    } else if (genMap.containsKey(getGenType().getId())){
      if(deps==null)
        deps = AttributeStorage.formPredDeps(getPredicate(), targ);
      ret = genMap.get(getGenType().getId()).getSingleValue(targ, i2,deps);
    }
    if (ret == null) return AttributeType.getDefaultValue(getAttributeType());
    return ret;
  }

  public ListValue getRandValues(boolean renew) {
    TypedValue tv = settings.get(RAND_VALUES);
    ListValue l = null;
    if(tv != null && tv.getType()==AttributeType.LIST)
      l = (ListValue) tv.getValue();
    if (l == null || renew || (l.getValues()).size() != getItemsCount()) {
      l = new ListValue(getAttributeListType(), generateSet(getItemsCount()));
      setRandValues(l);
    }
    return l;
  }

  public void setRandValues(ListValue v) {
    settings.put(RAND_VALUES, new TypedValue(AttributeType.LIST,v));
  }

  public Object getValue(AttributeStorage targ,Set<String >deps) {
    if (getAttributeType().equals(AttributeType.LIST)) {
      List<Object> list = new ArrayList<>();
      int numchilds = getItemsCount();
      for (int i = 0; i < numchilds; i++) {
        String ret = getSingleValue(targ, i, deps).toString();
        if (ret != null) list.add(ret);
      }
      return new ListValue(getAttributeListType(), list);
    }
    return getSingleValue(targ, -1, deps);
  }

  public void setIdType(AttributeValueIteratorType tp) {
    settings.put(SETT_TYPE, new TypedValue(AttributeType.STRING, tp.getId()));

  }

  public void setIteration(int it) {
    // int old = iter;
    // th/is.iter = it;
    // if(old!=it)
    setValue(SETT_ITERATION,AttributeType.INT, it);
    // updateMaster();
  }

  public void setMax(int max) {
    /*
     * int old = this.max,oldm=min; if (max > this.min) { this.max = max; }
     * else { this.max = min; min = max; } if(old!=max || oldm!=min)
     */
    setValue(SETT_MAX,AttributeType.INT, max);
    // updateMaster();
  }

  private void setValue(String key,AttributeType at, Object value) {
    settings.put(key, new TypedValue(at,value));
  }

  public void setMin(int min) {
    /*
     * int old = this.min,oldmx=this.max; if (max > min) { this.min = min; }
     * else { this.min = max; max = min; } if(oldmx!=max || old!=min)
     */
    setValue(SETT_MIN,AttributeType.INT, min);
    // updateMaster();
  }

  public String getFormula() {
    if (getGenType().getId().equals(AttributeValueIteratorType.FORMULA.getId()))
      return getSettsValue(AttributeType.STRING, SETT_FORMULA, "").toString();
    return null;
  }

  public void setFormula(String value, boolean butch) {
    // if (!predicate.equals(value)) {
    // predicate = value;
    // if (!butch)
    setValue(SETT_FORMULA, AttributeType.STRING, value);
    // if (!butch)
    // updateMaster();
    // }
  }

  @Override
  public Map<String, TypedValue> getSettings() {
    return settings;
  }

  @Override
  public void loadSettings(Map<String, TypedValue> attributes) {
    this.settings = new HashMap<>();
    for(Entry<String, TypedValue> s:attributes.entrySet()){
        settings.put(s.getKey(), s.getValue());
    }
    TypedValue at = attributes.get(SETT_TYPE);
    if(at == null)
      at = new TypedValue(AttributeType.STRING, AttributeValueIteratorType.FORMULA_ID);
    setGeneratorType(AttributeValueIteratorType.fromId(at.getValue().toString()));
//    if (settings.containsKey(SETT_ITERTYPE) && settings.get(SETT_ITERTYPE).getValue() instanceof String)
//      settings.put(
//          SETT_ITERTYPE,new TypedValue(AttributeType.STRING, AttributeValueIteratorType.fromId(settings.get(SETT_ITERTYPE).getValue().toString())));
//    ittype =
//        (AttributeValueIteratorType)
//          getSettsValue(
//                SETT_ITERTYPE,
//                AttributeValueIteratorType.fromId(AttributeValueIteratorType.FORMULA_ID));
  }

  public AttributeGenerator copy() {
    return copy(true);
  }

  public AttributeGenerator copy(boolean newId) {
    AttributeGenerator ag =
        new AttributeGenerator(
            newId ? UUID.randomUUID() : genuid,
            new HashMap<>(settings));
    return ag;
  }

  public UUID getUUID() {
    return genuid;
  }

  @Override
  public int compareTo(RequalityIterator arg0) {
    if (!(arg0 instanceof AttributeGenerator)) return -1;
    // TODO Auto-generated method stub
    return getUUID().compareTo(((AttributeGenerator) arg0).getUUID());
  }

  public boolean weakEquals(AttributeGenerator generator) {
    // TODO compare
    return equals(generator);
  }

}
