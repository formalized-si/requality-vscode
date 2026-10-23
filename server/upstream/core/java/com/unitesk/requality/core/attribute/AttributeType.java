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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public enum AttributeType {
  INT,
  FLOAT,
  BOOL,
  STRING,
  HTML,
  LIST,
  RESOURCE,
  REFERENCE,
  DATE, // TODO
  ENUM,
  ENUM_DEFINITION,
  TREENODE,
  MAP;
  private static AttributeType[] visvalues;
  
  private static AttributeType[] vislistvalues;
  
  private static AttributeType[] simplevalues;
  private final static Set<AttributeType> ignored = new HashSet<>();
  static {
	  ignored.add(RESOURCE);
	  ignored.add(ENUM);
	  ignored.add(TREENODE);
	  ignored.add(MAP);
  }
  private static boolean isIgnoredVisual(AttributeType at) {
    return ignored.contains(at);
  }
  public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

  static {
    visvalues = new AttributeType[values().length - ignored.size()];
    int i = 0;
    for (AttributeType at : values()) if (!isIgnoredVisual(at)) 
      visvalues[i++] = at;
    simplevalues = new AttributeType[visvalues.length-3];
    vislistvalues = new AttributeType[visvalues.length-1];
    i = 0;
    int j = 0;
    for (AttributeType at : visvalues)
      if (at != ENUM_DEFINITION){
        vislistvalues[j++] = at;
        if(at != LIST && at != HTML)
          simplevalues[i++] = at;
      
      }
  }

  public static AttributeType[] visibleValues() {
    return visvalues;
  }

  public static AttributeType[] visibleListValues() {
    return vislistvalues;
  }

  public static AttributeType[] simpleValues() {
    return visvalues;
  }

  public Object fromString(String string) {
    if (string == null)
      return null;
    if (this == INT) {
      return Integer.valueOf(string);
    }
    if (this == FLOAT) {
      return Double.valueOf(string);
    }
    if (this == BOOL) {
      return Boolean.valueOf(string);
    }
    // TODO: LIST and Resource
    return string;
  }

  public static Object getDefaultValue(AttributeType attributeType) {
    if (attributeType == INT) return 0;
    if (attributeType == FLOAT) return 0.0;
    if (attributeType == BOOL) return false;
    if (attributeType == STRING) return "";
    if (attributeType == HTML) return "";
    if (attributeType == REFERENCE) return ".";
    if (attributeType == ENUM) return new AppliedEnumValue("", null, AttributeType.STRING, "");
    if (attributeType == LIST)
      return new ListValue(AttributeType.STRING, Arrays.asList(new Object[] {""}));
    if (attributeType == ENUM_DEFINITION) {
      return new EnumDefinitionValue(AttributeType.STRING, new ArrayList<EnumValue>());
    }
    if (attributeType == DATE) {
      Date d = new Date();
      return DATE_FORMAT.format(d);
    }
    return null;
  }

  public static AttributeType detectType(Object object) {
    if (object instanceof String) return AttributeType.STRING; // or reference...//or date))
    if (object instanceof Integer) // int??
    return AttributeType.INT;
    if (object instanceof Boolean) // int??
    return AttributeType.BOOL;
    if (object instanceof Double || object instanceof Float) return AttributeType.FLOAT;
    if (object instanceof EnumDefinitionValue) // boolean?
    return AttributeType.ENUM_DEFINITION;
    if (object instanceof AppliedEnumValue) return AttributeType.ENUM;
    if (object instanceof ListValue) return AttributeType.LIST;
    return null;
  }

  public static TypedValue getTypedValue(Object object) {
    if(object!=null && object instanceof TypedValue)
      return ((TypedValue)object);
    else
    {
      AttributeType at = detectType(object);
      return new TypedValue(at, object);
    }
  }
}
