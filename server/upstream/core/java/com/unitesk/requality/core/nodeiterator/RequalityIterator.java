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
package com.unitesk.requality.core.nodeiterator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.unitesk.requality.core.attribute.AttributeGenerator;
import com.unitesk.requality.core.model.TreeNode;

public abstract class RequalityIterator {
  protected Map<TreeNode, Set<String>> dependencies = new HashMap<>();
  protected UUID genuid;
  protected RequalityIteratorType type;
  private static Map<RequalityIteratorType, Class<? extends RequalityIterator>> types =
      new HashMap<>();

  static {
    types.put(RequalityIteratorType.ATTRIBUTE, AttributeGenerator.class);
    types.put(RequalityIteratorType.CHILD, ReuseRequalityChildIterator.class);
  }


  public RequalityIterator(UUID uid, RequalityIteratorType tp) {
    type = tp;
    genuid = uid;
  }

  public RequalityIteratorType getType() {
    return type;
  }

  @Override
  public String toString() {
    return "type: " + type + " target: ";
  }

  @Override
  public boolean equals(Object obj) {
    if (super.equals(obj)) return true;
    if (!(obj instanceof RequalityIterator)) {
      return false;
    }
    RequalityIterator it = (RequalityIterator) obj;
    return genuid != null && genuid.equals(it.genuid) || genuid == null && it.genuid == null;
  }

  public static RequalityIterator fromAttributeString(UUID it, String s) {
    RequalityIteratorType nit = RequalityIteratorType.ATTRIBUTE;
    try {
      UUID uid = null;
      if (s != null && !s.equals("")) {
        nit = RequalityIteratorType.values()[Integer.parseInt(s.substring(0, s.indexOf("|")))];
        s = s.substring(s.indexOf("|") + 1);
        if (!s.isEmpty() && s.startsWith("UUID:")) {
          String ts = s.substring(5, s.length());
          int t = ts.indexOf("|");
          int end = t != -1 ? t : s.length();
          if (end > 0) uid = UUID.fromString(ts.substring(0, end - 1));
          if (t > -1) s = ts.substring(end + 1);
        }
      }
      try {
        return getOne(uid, it, nit, s);
      } catch (Exception e) {
      }
    } catch (Exception e) {
      //Initialisation failed
    }
    return null;
  }

  public String toAttributeString() {
    return Integer.toString(type.ordinal()) + "|" + (genuid != null ? "UUID:" + genuid : "UUID:");
  }


  public static RequalityIterator getOne(UUID guid, UUID it, RequalityIteratorType nit) {
    return getOne(guid, it, nit, "");
  }

  public static RequalityIterator getOne(UUID guid, UUID it, RequalityIteratorType nit, String s) {
    try {
      return types
          .get(nit)
          .getConstructor(new Class[] {UUID.class, UUID.class, String.class})
          .newInstance(new Object[] {guid, it, s});
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}
