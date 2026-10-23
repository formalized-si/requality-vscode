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
package com.unitesk.requality.core.fi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.unitesk.requality.core.attribute.AppliedEnumValue;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;

public class InspectionStep extends Requirement implements IInspectionStep {
  public static final String ATTR_INSP_OBJECT = "inspObject";
  public static final String TYPE_NAME = "InspectionStep";
  public static final String ATTR_RULE = "Rule";
  public static final String ATTR_STATE = "State";
  public static final String ATTR_RESPONSE = "Response";
  public static final String ATTR_QUESTION = "Question";
  public static final String ATTR_PREV_INSP = "PrevInsp";
  public static final String ATTR_STATE_DEF = "ISState";
  public static final String ATTR_RESP_DEF = "ISResp";
  public static final String[] ATTR_STATE_DEF_VALUES =
      new String[] {"Открыто", "Заметка", "Снято", "Устранено"};
  public static final String[] ATTR_RESP_DEF_VALUES =
      new String[] {
        "Устранено",
        "Отклонено",
        "Не рассматривалось",
        "Требует устранения",
        "Требует обсуждения",
        "Не рассматривалось"
      };

  public static String getVisibleType() {
    return "FRIssue";
  }

  public InspectionStep() {
    this(null, null);
  }

  public InspectionStep(TreeDB db, UUID uuid) {
    super(db, uuid);
    if(uuid!=null)
    {
      attrs()
        .setConstAttribute(
            InspectionStep.ATTR_STATE,
            new AppliedEnumValue(
                "Открыто", null, AttributeType.STRING, InspectionStep.ATTR_STATE_DEF),
            0);
      attrs()
          .setConstAttribute(
              InspectionStep.ATTR_RESPONSE,
              new AppliedEnumValue(
                  "Не рассматривалось", null, AttributeType.STRING, InspectionStep.ATTR_RESP_DEF),
              0);
      attrs()
          .setAttribute(new AttributeDefinition(uuid, 
              ATTR_INSP_OBJECT,
              new ReferenceValueProvider(
                  AttributeType.REFERENCE, Requirement.getTypeRootQId()),
              0)); //initial ref on catalog
    }
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.fi.IInspectionStep#getRelatedElements()
   */
  @Override
  public List<String> getRelatedElements() {
    List<TreeNode> nodes = attrs().getLinkAttributeValues(ATTR_INSP_OBJECT);
    if (nodes != null){
      List<String> nodes2 = new LinkedList<>();
      for(TreeNode node:nodes)
        nodes2.add(node.getExternalForeignID());
      return nodes2;
    }
    return Collections.emptyList();
  }

  public List<TreeNode> getRelatedTreeNode() {
    List<TreeNode> nodes = attrs().getLinkAttributeValues(ATTR_INSP_OBJECT);
    if (nodes != null){
      return nodes;
    }
    return Collections.emptyList();
    
  }
  public Map<UUID, String> getRelatedNodesStoredVersions() {
    Map<UUID, String> nv = attrs().getLinkAttributeStoredVersions(ATTR_INSP_OBJECT);
    return null;
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.fi.IInspectionStep#getRule()
   */
  @Override
  public String getRule() {
    return attrs().getStringAttributeValue(ATTR_RULE, "");
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.fi.IInspectionStep#getResponse()
   */
  @Override
  public String getResponse() {
    return attrs().getStringAttributeValue(ATTR_RESPONSE, "Не рассматривалось");
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.fi.IInspectionStep#getPrevNumber()
   */
  @Override
  public String getPrevNumber() {
    return attrs().getStringAttributeValue(ATTR_PREV_INSP, "");
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.fi.IInspectionStep#getState()
   */
  @Override
  public String getState() {
    return attrs().getStringAttributeValue(ATTR_STATE, "Открыто");
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.fi.IInspectionStep#getQuestion()
   */
  @Override
  public String getQuestion() {
    return attrs().getStringAttributeValue(ATTR_QUESTION, "");
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.core.fi.IInspectionStep#getInspection()
   */
  @Override
  public IInspectionResult getInspection() {
    TreeNode par = getWorkParent();
    while (par != null && !par.getType().equals(InspectionResult.TYPE_NAME))
      par = par.getWorkParent();
    return (IInspectionResult) par;
  }

  public static void initEnums(TreeNode root) {
    TreeNode node = root.getTreeDB().getRootNode();
    List<EnumValue> evs = new ArrayList<EnumValue>();
    if (node.attrs().getAttribute(ATTR_STATE_DEF) == null) {
      for (String s : ATTR_STATE_DEF_VALUES) evs.add(new EnumValue(s, null));
      node.attrs().setConstAttribute(ATTR_STATE_DEF, new EnumDefinitionValue(AttributeType.STRING, evs),
          0);
    }
    if (node.attrs().getAttribute(ATTR_RESP_DEF) == null) {
      evs = new ArrayList<EnumValue>();
      for (String s : ATTR_RESP_DEF_VALUES) evs.add(new EnumValue(s, null));
      node.attrs().setConstAttribute(ATTR_RESP_DEF, new EnumDefinitionValue(AttributeType.STRING, evs),
          0);
    }
    node.commitAttributes();
  }

}
