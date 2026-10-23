package com.unitesk.requality.core.attribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.ValueProviderFactory;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public class RequiredAttributesRule {
  private static String ATTR_MAP = "_requiredAttrsMap";
  private Map<String, Map<String, AttributeDefinition>> map = new HashMap<>();
  private static Set<String> PERMITTED_KEYS;
  static{
    PERMITTED_KEYS = new HashSet<>();
    PERMITTED_KEYS.add(TreeNode.ATTR_ID);
    PERMITTED_KEYS.add(TreeNode.ATTR_TYPE);
    PERMITTED_KEYS.add(TreeNode.ATTR_CHILDREN);
    PERMITTED_KEYS.add(TreeNode.ATTR_CLONE_PARENT);
    PERMITTED_KEYS.add(TreeNode.ATTR_CHILDREN_UUIDS);
    PERMITTED_KEYS.addAll(ValueProviderFactory.getVirtualAttrNames());
    PERMITTED_KEYS.add(TreeNode.ATTR_GENERATORS);
    PERMITTED_KEYS.add(TreeNode.ATTR_PARENT);
    PERMITTED_KEYS.add(TreeNode.ATTR_POSITION);
    PERMITTED_KEYS.add(TreeNode.ATTR_REPO_STATE);
    PERMITTED_KEYS.add(TreeNode.ATTR_REVERT_LINKS);
    PERMITTED_KEYS.add(TreeNode.ATTR_SHOWN_CHILDREN);
    PERMITTED_KEYS.add(TreeNode.ATTR_LOCATIONS_DESCRIPTION);
  }
  public static boolean isPermittedKey(TreeDB db, String name) {
    String aname = SystemNames.getOne(db).getSysName(name);
    if(aname!=null)
      name = aname;
    return PERMITTED_KEYS.contains(name);
  }
  public Map<String, Map<String, AttributeDefinition>> getMap(){
    return map;
  }
  public void setMap(TreeDB db, Map<String, Map<String, AttributeDefinition>> mp){
    this.map = mp;
    for(String s:mp.keySet())
      for(String key:new HashSet<>(mp.get(s).keySet()))
        if(isPermittedKey(db, key))
          mp.get(s).remove(key);
  }
  public static void save(TreeNode nodeToStore, RequiredAttributesRule rar){
    boolean ch = false;
    if(rar == null) {
      ch = nodeToStore.attrs().removeAttribute(ATTR_MAP) != null;
    }else{
      List<Object> lvs = new ArrayList<>();
      for(Entry<String, Map<String, AttributeDefinition>> en: rar.map.entrySet()){
        List<Object> lvs2 = new ArrayList<>();
        
        List<Object> lvs3 = new ArrayList<>();
        lvs3.add(en.getKey());
        lvs2.add(new ListValue(AttributeType.STRING, lvs3));//type
        
        for(Entry<String, AttributeDefinition> en2:en.getValue().entrySet()){
          AttributeDefinition ad = en2.getValue();
          lvs3 = new ArrayList<>();
          
          List<Object> lvs4 = new ArrayList<>();
          lvs4.add(en2.getKey());
          lvs3.add(new ListValue(AttributeType.STRING, lvs4));
          
          lvs4 = new ArrayList<>();
          lvs4.add(ad.getRawValue().getValue());
          lvs3.add(new ListValue(en2.getValue().getType(), lvs4));
          
          lvs2.add(new ListValue(AttributeType.LIST, lvs3));
        }
        lvs.add(new ListValue(AttributeType.LIST, lvs2));
      }
      ListValue lv = new ListValue(AttributeType.LIST, lvs);
      nodeToStore.attrs().setAttribute(
          new AttributeDefinition(nodeToStore.getUUId(), ATTR_MAP,
              new ConstValueProvider(AttributeType.LIST, lv), Flag.HIDDEN));
      ch = true;
    }
    if(ch)
      nodeToStore.commitAttributes();
  }
  public static RequiredAttributesRule load(TreeNode nodeToStore){
    Attribute a = nodeToStore.attrs().getAttribute(ATTR_MAP);
    Map<String, Map<String, AttributeDefinition>> map = new HashMap<>();
    if(a!=null){
      ListValue lv = a.getGValue();
      if(lv!=null){
        for(Object o: lv.getValues()){
          ListValue lvl2 = (ListValue)o;
          String type = ((ListValue)lvl2.getValues().get(0)).getValues().get(0).toString();
          if(!map.containsKey(type))
            map.put(type, new HashMap<>());
          for(int i = 1;i<lvl2.getValues().size();i++)
          {
            ListValue lvl3 = (ListValue) lvl2.getValues().get(i);
            ListValue keys = ((ListValue)lvl3.getValues().get(0));
            String key1 = keys.getValues().get(0).toString(),
                key2 = SystemNames.getOne(nodeToStore.getTreeDB()).getVisSysName(key1);
            if(key2 == null)
              key2 = key1;
            ListValue lvl4 = (ListValue)lvl3.getValues().get(1);
            AttributeType at = lvl4.getType();
            Object val = lvl4.getValues().get(0);
            if(!map.get(type).containsKey(key1))
              map.get(type).put(key1, new AttributeDefinition(null, key2, 
                  new ConstValueProvider(at, val)));
          }
        }
      }
    }
    RequiredAttributesRule rar = new RequiredAttributesRule();
    rar.setMap(nodeToStore.getTreeDB(),map);
    return rar;
  }
  public AttributeDefinition getDefinition(String type, String key) {
    if(!map.containsKey(type))
      return null;
    return map.get(type).get(key);
  }
  public boolean isSame(Map<String, Map<String, AttributeDefinition>> mapVals) {
    Set<String> keyNew = new HashSet<>(mapVals.keySet());
    for(Entry<String, Map<String, AttributeDefinition>> en:map.entrySet()) {
      String key = en.getKey();
      Map<String, AttributeDefinition> val1 = en.getValue(),
                                       val2 = mapVals.get(key);
      keyNew.remove(key);
      if(val1 == null ^ val2 == null || val1!=null && val2!=null && !val1.keySet().equals(val2.keySet()))
        return false;
      if(val1 == null || val2 == null)
        continue;

      for(String s2:val1.keySet())
      {
        AttributeDefinition ad1 = val1.get(s2);
        AttributeDefinition ad2 = val2.get(s2);
        if(!ad1.getRawValue().equals(ad2.getRawValue()))
          return false;
      }
      
    }
    if(!keyNew.isEmpty()) {
      for(Entry<String, Map<String, AttributeDefinition>> en:mapVals.entrySet()) 
        if(!en.getValue().isEmpty())
          return false;
      }
    
    return true;
  }
  public static boolean isChanged(ChangesSet<Attribute> params) {
    return params.contains(ATTR_MAP);
  }
}
