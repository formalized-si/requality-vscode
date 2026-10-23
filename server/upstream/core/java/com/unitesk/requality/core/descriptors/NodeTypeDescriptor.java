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
package com.unitesk.requality.core.descriptors;

import com.unitesk.requality.core.model.TreeNode;

public class NodeTypeDescriptor {
  private String name;
  private Class<? extends TreeNode> clazz;
  private String icon;
  private String pluginId;

  public NodeTypeDescriptor(
      String name, Class<? extends TreeNode> clazz, String icon, String pluginId) {
    this.name = name;
    this.clazz = clazz;
    this.icon = icon;
    this.pluginId = pluginId;
  }

  public String getName() {
    return name;
  }

  public Class<? extends TreeNode> getClazz() {
    return clazz;
  }

  public String getIcon() {
    return icon;
  }

  public String getPluginId() {
    return pluginId;
  }
}
