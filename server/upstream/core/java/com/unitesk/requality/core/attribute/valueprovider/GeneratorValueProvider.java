package com.unitesk.requality.core.attribute.valueprovider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeGenerator;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.nodeiterator.AttributeValueIteratorType;
import com.unitesk.requality.core.storage.NodeMapper;

public class GeneratorValueProvider implements IValueProvider {
  GeneratorValueProvider(UUID guid,Map<String,TypedValue> setts){
    gen = new AttributeGenerator(
        guid!=null?guid:UUID.randomUUID(),
        filterMap(setts));
    
  }
  private static Map<String, TypedValue> filterMap(Map<String, TypedValue> map) {
	  Map<String, TypedValue> ret = new HashMap<>(map);
    ret.remove("attribute_type");
    ret.remove("atribute_list_type");
    ret.remove("uuid");
    return ret;
  }
  private AttributeGenerator gen;
  public static String TYPE="GeneratorValueProvider";
  public GeneratorValueProvider(AttributeGenerator ag) {
    gen = ag;
  }
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    return new TypedValue(gen.getAttributeType(), gen.getValue(as,getDependencies(as)));
  }

  @Override
  public boolean equals(Object obj) {
    if(obj == null)
      return false;
    if(!(obj instanceof GeneratorValueProvider))
      return false;
    GeneratorValueProvider alt = (GeneratorValueProvider)obj;
    return gen.equals(alt.gen);
  }
  
  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    if(gen.getGeneratorType().getId().equals(AttributeValueIteratorType.FORMULA_ID))
    {
      String formula = gen.getFormula();
      if(formula == null)
        return NULLDEP;
      Set<String> dep = new HashSet<>();
      for(String s:as.getWorkAttributeKeys(true)){
        if(formula.indexOf(s)>-1)
          dep.add(s);
      }
      return dep;
    }
    return NULLDEP;
  }
//  @Override
//  public IValueProvider clone() {
//    return new GeneratorValueProvider(gen);//TODO can cause bugs?
//  }
  public AttributeGenerator getGenerator() {
    return gen;
  }
  @Override
  public String getType() {
    return TYPE;
  }
  @Override
  public TypedValue getRawValue() {
    return null;
  }
  @Override
  public Map<String, Object> getRawStores() {
    Map<String,Object> alls = new HashMap<>(gen.getSettings());
    alls.put("uuid", gen.getUUID());
//    alls.put("attribute_type", gen.getAttributeType().ordinal());
//    if (gen.getAttributeListType() != null)
//      alls.put("atribute_list_type", gen.getAttributeListType().ordinal());
    alls.put("generator_type", gen.getGeneratorType().getId());
    return alls;
  }
  @Override
  public void attach(TreeNode node) {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void detach(TreeNode node) {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void saveJSON(JSONObject vpo) throws JSONException {
    vpo.put("uuid", gen.getUUID());
    //vpo.put("generator_type", gen.getGeneratorType().getId());
    Map<String,Object> alls = new HashMap<>(gen.getSettings());
    for(Entry<String, Object> en:alls.entrySet()){
      JSONObject obj = new JSONObject();
      TypedValue tv = AttributeType.getTypedValue(en.getValue());
      NodeMapper.valueToJSON(obj,tv.getType(),tv.getValue(),"value");
      if(tv.getType()!=AttributeType.STRING)
        obj.put("atype", tv.getType());
      vpo.put(en.getKey(), obj);
    }
    
  }
  @Override
  public AttributeType getAttributeType() {
    return gen.getAttributeType();
  }
}
