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

import java.io.File;
import java.net.URI;
import java.util.UUID;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.tool.CoreUtils;

public class ReportData extends ReportSettings {
  public static final String TYPE_NAME = "ReportData";
  public static final String ATTR_FIRST_MROOT = "_firstRequirementRoot";

  public ReportData() {
    this(null, null);
  }

  public static String getVisibleType() {
    return "Report";
  }

  public ReportData(TreeDB db, UUID uuid) {
    super(db, uuid);
  }

  public String getStartRequirementQId() {
    return attrs().getStringAttributeValue(ATTR_ROOT_REQUIREMENT, Requirement.getTypeRootQId());
  }

  public URI getSaveDir() {
    try {
      return getNodeResourcesPath();
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  public String getTemplateId() {
    return attrs().getStringAttributeValue(ATTR_TEMPLATE);
  }

  public boolean reportExists() {
    String gen = attrs().getStringAttributeValue(ReportData.ATTR_LAST_GENERATED);
    if (gen == null) return false;
    return new File(URI.create(getSaveDir() +"/"+ CoreUtils.URLEncode(gen))).exists();
  }
}
