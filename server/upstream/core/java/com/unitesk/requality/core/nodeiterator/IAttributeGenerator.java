package com.unitesk.requality.core.nodeiterator;

import java.util.Map;
import java.util.Set;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;

public interface IAttributeGenerator {
  Map<String, TypedValue> getSettings();

  Object getSingleValue(AttributeStorage targ, int i2,Set<String>deps);

  int getItemsCount();

  String getLabel();

  AttributeType getAttributeType();

  AttributeType getAttributeListType();

  void loadSettings(Map<String, TypedValue> attributes);
}
