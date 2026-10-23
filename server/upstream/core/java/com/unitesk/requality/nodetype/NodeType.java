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
package com.unitesk.requality.nodetype;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.CoreUtils;

public class NodeType extends TreeNode {
  public static final String TYPE_NAME = "NodeType";
  private static final String ATTRIBUTE_CLASSNAME = "_classname";
  private static final String ATTRIBUTE_VISIBLE_CLASSNAME = "_visclassname";
  private static final String ATTRIBUTE_ICON = "_iconResource";
  private Class<? extends TreeNode> clazz;

  public NodeType(String name, TreeDB db) {
    super(db);
    initVariables(db, null);
    setId(name, true);
    setType(TYPE_NAME, true);
  }

  public NodeType(TreeDB db, UUID uuid) {
    super(db, uuid);
  }

  public String getName() {
    String name = getId();
    if(name == null)
      name = attrs().getWorkAttributeDefinition(ATTR_ID).getRawValue().getValue().toString();
    return name;
  }

  @Override
  public String toString() {
    return "TypeNode '" + getName() + "' (" + getUUId() + ")";
  }

  public void setVisibleClassName(Class<? extends TreeNode> clazz) {
    String name = CoreUtils.splitWords(clazz.getSimpleName());
    Method m;
    try {
      m = clazz.getMethod("getVisibleType", new Class[0]);
      if (m != null) {
        name = (String) m.invoke(clazz, new Object[0]);
      }

    } catch (SecurityException e) {
    } catch (NoSuchMethodException e) {
    } catch (IllegalArgumentException e) {
    } catch (IllegalAccessException e) {
    } catch (InvocationTargetException e) {
    }
    attrs().setAttribute(new AttributeDefinition(getUUId(), ATTRIBUTE_VISIBLE_CLASSNAME, 
        new ConstValueProvider(AttributeType.STRING, name), Flag.HIDDEN), true);
  }

  public String getVisibleClassName() {
    return attrs().getStringAttributeValue(ATTRIBUTE_VISIBLE_CLASSNAME, "undefined");
  }

  public void setClassName(String className) {
    attrs().setAttribute( new AttributeDefinition(getUUId(), ATTRIBUTE_CLASSNAME,
        new ConstValueProvider(AttributeType.STRING, className),Flag.HIDDEN), true);
  }

  public void setClass(Class<? extends TreeNode> clazz) {
    setClassName(clazz.getName());
    this.clazz = clazz;
    setVisibleClassName(clazz);
  }

  @SuppressWarnings("unchecked")
  public Class<? extends TreeNode> getTypeClass() {
    if (clazz != null) {
      return clazz;
    }
    String className = attrs().getStringAttributeValue(ATTRIBUTE_CLASSNAME);
    if (className == null) {
      return null;
    }
    try {
      return (Class<? extends TreeNode>) Class.forName(className);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    return null;
  }

  public String getIconName() {
    if (attrs().getStringAttributeValue(ATTRIBUTE_ICON) == null) return getId();
    return attrs().getStringAttributeValue(ATTRIBUTE_ICON);
  }

  public boolean setName(String icon) {
    attrs().setConstAttribute(ATTRIBUTE_ICON, icon,Flag.HIDDEN);
    return true;
  }

  @Override
  public boolean equals(Object obj) {
    if (super.equals(obj)) return true;
    if (obj instanceof NodeType) {
      NodeType t = (NodeType) obj;
      if (t.getName().equals(getName())) return true;
    }
    return false;
  }

  @Override
  public boolean isRestricted() {
    return true;
  }
}
