package com.unitesk.requality.core.nodeiterator;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AttributeValueIteratorType {
  public static final String CYCLE_ID = "CYCLE", RANDOM_ID = "RANDOM", FORMULA_ID = "BY_FORMULA";
  public static AttributeValueIteratorType CYCLE, RANDOM, FORMULA;
  private String id;
  private static Set<String> ids = new HashSet<>();
  private static Map<String, AttributeValueIteratorType> vals =
      new HashMap<>();
  private static Map<String, String> labelsMap = new HashMap<>();

  static {
    ids.add(CYCLE_ID);
    ids.add(RANDOM_ID);
    ids.add(FORMULA_ID);
    vals.put(CYCLE_ID, new AttributeValueIteratorType(CYCLE_ID));
    vals.put(RANDOM_ID, new AttributeValueIteratorType(RANDOM_ID));
    vals.put(FORMULA_ID, new AttributeValueIteratorType(FORMULA_ID));
    CYCLE = vals.get(CYCLE_ID);
    RANDOM = vals.get(RANDOM_ID);
    FORMULA = vals.get(FORMULA_ID);
  }

  private AttributeValueIteratorType(String type) {
    id = type;
  }

  public static AttributeValueIteratorType getAVIType(String id) {
    if (id == null) return vals.values().iterator().next();
    if (!ids.contains(id)) return null;
    if (!vals.containsKey(id)) vals.put(id, new AttributeValueIteratorType(id));
    return vals.get(id);
  }

  public static void registerAVIType(String st, String label) {
    getAVIType(st);
    labelsMap.put(st, label);
  }

  public static AttributeValueIteratorType fromId(String type) {
    return new AttributeValueIteratorType(type);
  }

  public String getId() {
    return id;
  }

  public String getLabel() {
    if (labelsMap.containsKey(id)) return labelsMap.get(id);
    return id;
  }

  public static Collection<AttributeValueIteratorType> values() {
    return vals.values();
  }

  @Override
  public String toString() {
    return getLabel();
  }
}
