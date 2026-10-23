package com.unitesk.requality.tool.reqif;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AttributeValues implements Iterable<AbstractAttributeValue> {
  public Map<String, AbstractAttributeValue> valuesByAttributeId =
      new HashMap<>();
  private SpecObjectType sot;

  public AttributeValues(SpecObjectType definition) {
    sot = definition;
  }

  public void setValue(String definition, AbstractAttributeValue value) {
    valuesByAttributeId.put(definition, value);
  }

  public AbstractAttributeValue getValue(AttributeDefinition attributeDefinition) {
    AbstractAttributeValue aav = valuesByAttributeId.get(attributeDefinition.getId());
    if (aav == null) return attributeDefinition.datatype.getDefaultValue(attributeDefinition);
    return aav;
  }

  @Override
  public Iterator<AbstractAttributeValue> iterator() {
    //sot.attributesMap.keySet();
    List<AbstractAttributeValue> list = new LinkedList<>();
    for (AttributeDefinition ad : sot.getDefinitions()) {
      list.add(getValue(ad));
    }
    return list.iterator();
  }

  public boolean isSimilar(AttributeValues values) {
    Set<String> keys = new HashSet<>(values.valuesByAttributeId.keySet());
    for (String s : valuesByAttributeId.keySet()) {
      //RLogger.logInfo(valuesByAttributeId.get(s).definition.getName());
      if (!keys.contains(s)) {
        String key = valuesByAttributeId.get(s).definition.getName();
        if (!key.equals("ReqIF.ChapterName") && !key.equals("Тип объекта")) return false;
      } else {
        keys.remove(s);
        AbstractAttributeValue aav = valuesByAttributeId.get(s),
            aav2 = values.valuesByAttributeId.get(s);
        if (aav.getString() == null && aav2.getString() == null) {
          //eq
        } else if (aav.getString() == null || aav2.getString() == null) return false;
        else if (!aav.getString().equals(aav2.getString())) return false;
      }
    }
    return keys.size() == 0;
  }
}
