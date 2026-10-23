package com.unitesk.requality.core.sorting;

import java.util.HashMap;
import java.util.Map;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;

public class SortingSettings {
  public static enum SortingMethod {
    BY_NAMES,
    BY_LOCATIONS,
    BY_INDEX
  };

  private SortingMethod method;
  private boolean sep;
  public static final String SORT_METHOD = "_sorting_method";
  public static final String SORT_SEP_TYPES = "_separate_types_in_sort";
  public static final String SORT_BY_LOCATIONS = "_sort_by_locations";
  public static final String SORT_BY_INDEX = "_sort_by_index";
  private static Map<SortingMethod,Map<Boolean,SortingSettings>> map = new HashMap<>();
  private static SortingSettings defaultSortSettings =
      SortingSettings.init(SortingMethod.BY_INDEX, false);


  public static SortingSettings init(SortingMethod m, boolean separate){
    if(!map.containsKey(m))
      map.put(m, new HashMap<>());
    if(!map.get(m).containsKey(separate))
      map.get(m).put(separate, new SortingSettings(m, separate));
    return map.get(m).get(separate);
  }
  private SortingSettings(SortingMethod m, boolean separate) {
    method = m;
    sep = separate;
  }

  public SortingMethod getMethod() {
    return method;
  }

  public boolean getSeparateByTypesFlag() {
    return sep;
  }
  public static boolean isSame(SortingSettings sm, SortingSettings sm2){
    return sm.getMethod()==sm2.getMethod()&&
        sm.getSeparateByTypesFlag()==sm2.getSeparateByTypesFlag();
  }
  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
  public static String getSortingName(SortingMethod sm) {
    switch (sm) {
      case BY_INDEX:
        return "By Index";
      case BY_LOCATIONS:
        return "By Locations";
      case BY_NAMES:
        return "By Names";
      default:
        break;
    }
    return "Unknown!";
  }
  public static SortingSettings load(TreeNode node){
    if (node == null) return defaultSortSettings;
    // old style
    Attribute al = node.attrs().getAttribute(SORT_BY_LOCATIONS);
    SortingMethod sm = SortingMethod.BY_NAMES;
    if (al != null
        && ((al.getValue().equals("true")
            || al.getValue().equals(true)))) sm = SortingMethod.BY_LOCATIONS;
    Attribute ai = node.attrs().getAttribute(SORT_BY_INDEX);
    if (ai != null
        && (ai.getValue().equals("true")
            || ai.getValue().equals(true))) sm = SortingMethod.BY_INDEX;
    // new style
    Attribute a = node.attrs().getAttribute(SORT_METHOD);
    if (a != null) {
      String aval = a.getValue().toString();
      if (aval != null) {
        try{
          SortingMethod t = SortingMethod.valueOf(aval);
          sm = t;
        }catch(IllegalArgumentException iae){
          RLogger.logError(iae);
        }
      } else sm = defaultSortSettings.getMethod();
    } else {
      if (al == null && ai == null) sm = defaultSortSettings.getMethod(); // buggy?
    }
    a = node.attrs().getAttribute(SORT_SEP_TYPES);
    boolean sepState =
        a != null
            ? (a.getValue().equals("true")
                || a.getValue().equals(true))
            : defaultSortSettings.getSeparateByTypesFlag();
    return SortingSettings.init(sm, sepState);
    
  }
}
