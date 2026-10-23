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

import java.util.UUID;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeDB;

public class TextNode extends Requirement {

  public static final String TYPE_NAME = "TextNode";
  public static final String ATTR_TNTYPE = "_textNodeType";

  {
    Attribute.registerStatusName(ATTR_TNTYPE);
  }

//  public String getTextNodeType() {
//    Attribute a = attrs().getAttribute(ATTR_TNTYPE);
//    return a == null ? "text" : a.getValue().toString();
//  }
//
//  public void setTextNodeType(String string) {
//    attrs().setConstAttribute(ATTR_TNTYPE, string,Flag.HIDDEN);
//  }
  public TextNode() {
    this(null, null);
  }
  public TextNode(TreeDB db, UUID uuid) {
    super(db, uuid);
  }

}
