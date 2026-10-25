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

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.SystemNames;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.report.ReportGenerationContext;
import com.unitesk.requality.report.template.TemplateFile;

public class ReportSettings extends TreeNode {
  public static SimpleDateFormat dataFormat =
      new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
  protected String lastTemplate = null;
  private static Map<String, String> staticsernames = new HashMap<>();
  private static Map<String, String> staticrevsernames = new HashMap<>();
  private static String[] defSelAttrsOrder = new String[]{SystemNames.SEQUENTIAL_ID_REF_NAME, "_description", "Вид", "Производное"};
  static {
    staticsernames.put("Текст", TreeNode.ATTR_DESCRIPTION);
    staticsernames.put("SeqID", SystemNames.SEQUENTIAL_ID_REF_NAME);
    staticsernames.put("Тип требования", "Вид");
    staticsernames.put("Пр.", "Производное");
    staticsernames.put("Идентификатор", TreeNode.ATTR_USERVISIBLEID);
    for(String s:staticsernames.keySet())
      staticrevsernames.put(staticsernames.get(s), s);
    staticsernames = Collections.unmodifiableMap(staticsernames);
    staticrevsernames = Collections.unmodifiableMap(staticrevsernames);
  }
  private INodeChangeListener ncl =
      new INodeChangeListener() {
        @Override
        public void deleted(TreeNode source) {}

        @Override
        public void attributeChange(TreeNode source, String what, Attribute o) {
          String old = null;
          if(o!=null && o.getValue()!=null)
            old = o.getGValue();
          if (source == ReportSettings.this
              && (what.equals(ATTR_TEMPLATE) || what.equals(TreeNode.ATTR_UNDEFINED))
              && old != null
              && (getLastTemplate() == null || !lastTemplate.equals(old))) {
            lastTemplate = old;
            // if(source.getTreeDB().getStorage() instanceof
            // ResourceStorage)
            // GenerateReportHandler.generateReport(ReportSettings.this);

          }
        }

        @Override
        public void moved(TreeNode source, TreeNode oldParent) {
          // TODO Auto-generated method stub

        }

        @Override
        public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
          if (what.contains(ATTR_TEMPLATE) || what.contains(TreeNode.ATTR_UNDEFINED))
            attributeChange(source, ATTR_TEMPLATE, what.getOld(ATTR_TEMPLATE));
        }

        @Override
        public void fireNodeResourceChange(
            TreeNode treeNode, String resName, Moment m) {
          // TODO Auto-generated method stub

        }
      };
  public static final String TYPE_NAME = "ReportSettings";
  public static final String ATTR_ROOT_REQUIREMENT = "_root_requiremnt_qid";
  public static final String ATTR_DATE = "date";
  public static final String ATTR_TEMPLATE = "_template_id";
  public static final String ATTR_LAST_GENERATED = "_last_generated";
  public static final String ATTR_GEN = "generating_in_process";
  public static final String ATTR_CP = "_allowCrossProject";
  public static final String ATTR_ROOT_INCLUDED = "_showRootData";
  public static final String ATTR_GN = "useProjectNumeration";
  public static final String ATTR_SEL_ATTR = "selAttrs";
  public static final String ATTR_WIDTH_ATTR = "selAttrsWidth";
  public static final String ATTR_SEL_FORMULA = "selFormula";
  public static final String ATTR_USER_DEFINED_FILENAME = "reportFileName";
  public static final String NEXT_REPORT_UUID = "nextReportUuid";
  

  public ReportSettings() {
    this(null, null);
  }

  protected String getLastTemplate() {
    if (lastTemplate == null) lastTemplate = this.attrs().getStringAttributeValue(ATTR_TEMPLATE);
    return lastTemplate;
  }

  public ReportSettings(TreeDB db, UUID uuid) {
    super(db, uuid);
    this.addChangeListener(ncl);
    // lastTemplate = attrs().getLocalStringAttributeValue(ATTR_TEMPLATE);
  }

  @Override
  public String getMultiTypeLabel() {
    return getTypeLabel();
  }

  public String getStartRequirementQId() {
    return attrs().getStringAttributeValue(ATTR_ROOT_REQUIREMENT, Requirement.getTypeRootQId());
  }

  public Map<String, Object> getData() {
    //TODO remove
    Map<String, Object> result = new HashMap<>();
    for (Entry<String, Attribute> en : attrs().getAvailableAttributes().entrySet()) {
      if (!en.getKey().startsWith("_") || 
    		  en.getKey().equals(ReportSettings.ATTR_CP)||
    		  en.getKey().equals(ReportSettings.ATTR_ROOT_INCLUDED))
      	if(en.getValue()!=null)
          result.put(en.getKey(), en.getValue().getValue());
    }
    return result;
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

  @Override
  public boolean canEditAttributesInTable() {
    return true;
  }

  public boolean isGenerating() {
    return attrs().getBooleanAttributeValue(ATTR_GEN);
  }

  public void setGenerating(boolean b) {
    attrs().setConstAttribute(ATTR_GEN, b, Flag.VIRTUAL| Flag.HIDDEN);
  }
  public void setCrossProjectFlag(boolean selection) {
    if(!selection)
      attrs().removeAttribute(ATTR_CP);
    else
      attrs().setConstAttribute(ATTR_CP, selection, Flag.HIDDEN);    
  }
  public void setRootIncludedFlag(boolean selection) {
    if(selection)
      attrs().removeAttribute(ATTR_ROOT_INCLUDED);
    else
      attrs().setConstAttribute(ATTR_ROOT_INCLUDED, selection, Flag.HIDDEN);    
  }
  public boolean getCrossProjectFlag(){
    return attrs().getBooleanAttributeValue(ATTR_CP, false);
  }

  public boolean getRootIncludedFlag(){
    return attrs().getBooleanAttributeValue(ATTR_ROOT_INCLUDED, true);
  }

  public Map<String, String> getSernames(){
    Map<String, String> sn = new HashMap<>(staticsernames);
    sn.put("SeqID", FIDUpdater.getFIDName(getTreeDB()));
    return sn;
  }

  public static Map<String, String> getRevservnames(TreeDB db) {
    Map<String, String> sn = new HashMap<>(staticrevsernames);
    sn.put(FIDUpdater.getFIDName(db), "SeqID");
    return sn;
  }
  public Map<String, String> getRevservnames() {
    return getRevservnames(getTreeDB());
  }
  public String[] getSelectedAttrs() {
    return attrs().getStringArrayAttributeValue(ATTR_SEL_ATTR, defSelAttrsOrder);
  }

  public static String[] getDefVisSelectedAttrs() {
    return staticrevsernames.keySet().toArray(new String[0]);
  }
  public static String[] getDefSelectedAttrs() {
    return defSelAttrsOrder;
  }
  public String[] getVisSelectedAttrs() {
    String[]atr = getSelectedAttrs();
    String[]vatr = new String[atr.length];
    Map<String, String> rn = getRevservnames();
    for(int i = 0;i<atr.length; i++){
      vatr[i] = atr[i];
      String sa = rn.get(vatr[i]);
      if(sa!=null)
        vatr[i] = sa;
    }
    return vatr;
  }
  public String[] getDefSelectedFormula() {
    String []va = getSelectedAttrs();
    String []ret = new String[va.length];
    Arrays.fill(ret, "");
    if(ret.length<va.length)
    {
      int max = ret.length;
      ret = Arrays.copyOf(ret, va.length);
      for(int i = max;i<va.length;i++)
        ret[i] = "";
    }
    for(int i = 0;i<ret.length;i++)
      if(va[i].equals("Производное"))
        ret[i] = "value == \"true\"?\"Пр\":value == \"false\"?\"\":value";
    return ret;
  }
  public String[] getDefAttrsWidth(){
    String[]va = getDefSelectedAttrs();
    String[] ret = new String[va.length];
    Arrays.fill(ret, "");
    if(ret.length<va.length)
    {
      int max = ret.length;
      ret = Arrays.copyOf(ret, va.length);
      for(int i = max;i<va.length;i++)
        ret[i] = "";
    }
    for(int i = 0;i<ret.length;i++)
      if(va[i].equals(TreeNode.ATTR_DESCRIPTION) && ret[i].isEmpty())
        ret[i] = "850px";
    return ret;
  }
  public String[] getSelectedAttrsWidth() {
    String []ret = attrs().getStringArrayAttributeValue(ATTR_WIDTH_ATTR, null);
    String[] va = getSelectedAttrs();
    if(ret == null){
      ret = new String[va.length];
      Arrays.fill(ret, "");
    }
    if(ret.length<va.length)
    {
      int max = ret.length;
      ret = Arrays.copyOf(ret, va.length);
      for(int i = max;i<va.length;i++)
        ret[i] = "";
    }
    for(int i = 0;i<ret.length;i++)
      if(va[i].equals(TreeNode.ATTR_DESCRIPTION) && ret[i].isEmpty())
        ret[i] = "850px";
    return ret;
  }

  public String[] getSelectedFormula() {
    String []ret = attrs().getStringArrayAttributeValue(ATTR_SEL_FORMULA, null);
    String []va = getSelectedAttrs();
    boolean def = false;
    if(ret == null){
      def = true;
      ret = new String[va.length];
      Arrays.fill(ret, "");
    }
    if(ret.length<va.length)
    {
      int max = ret.length;
      ret = Arrays.copyOf(ret, va.length);
      for(int i = max;i<va.length;i++)
        ret[i] = "";
    }
    for(int i = 0;i<ret.length;i++)
      if(def && va[i].equals("Производное"))
        ret[i] = "value == \"true\"?\"Пр\":value == \"false\"?\"\":value";
    return ret;
  }


  public Requirement getRootTargetRequirement(TreeDB instTree) {
    return getStartRequirement(instTree);
  }

  public String calcStartFileName(TemplateFile templ, TreeDB instTree, Requirement specNode, ReportGenerationContext context){
    if(specNode == null){
      specNode = getRootTargetRequirement(instTree);
      Requirement rootReq = (Requirement) instTree.getNode(Requirement.getTypeRootQId());
      List<TreeNode> mroots =
    		  context!=null?context.getMultipartRoots(instTree):
                instTree.getMultiPart(rootReq,
                  null, context).getMultipartRoots(context, specNode, false);
      if(mroots!=null && mroots.size()>0)
        specNode = (Requirement) mroots.iterator().next();
    }
    ReportData rd = context == null? null : context.getReportData();
    return templ.getOutputFileName(
    		rd!=null?rd.getUserDefinedFileName(templ)
    		:null,
    		instTree, specNode, this);
  }

  public String getStartFileName(TemplateFile templ, TreeDB instTree, ReportGenerationContext context) {
    String attrVal = attrs().getStringAttributeValue(ReportData.ATTR_LAST_GENERATED);
    if(attrVal == null)
      attrVal = calcStartFileName(templ, instTree, null, context);//not so good case 
    //TODO load from somewhere? store it to rdata?
    return attrVal;
  }
  private Requirement getStartRequirement(TreeDB instTree) {
    Requirement r = (Requirement) instTree.getNode(
            getStartRequirementQId(), com.unitesk.requality.core.model.Path.QID);
    if (r == null)
      for (UUID uid : instTree.getRootNode().getWorkChildrenUUIDs()) {
        TreeNode n = instTree.getNode(uid);
        if (Requirement.TYPE_NAME.equals(n.getType())) r = (Requirement) n;
	  }
    if (r == null)
      r = (Requirement) instTree.getNode(
            getStartRequirementQId(), com.unitesk.requality.core.model.Path.QID);
    return r;
  }
  public static String getTemplateSpecificUserDefinedTemplateFileNameAttribute(TemplateFile tf) {
    return ATTR_USER_DEFINED_FILENAME+"-"+tf.getTemplatePath();
  }

  public String getUserDefinedFileName(TemplateFile tf) {
    String s = attrs().getStringAttributeValue(ReportSettings.getTemplateSpecificUserDefinedTemplateFileNameAttribute(tf));
    if(s!=null)
      return s; 
	return attrs().getStringAttributeValue(ATTR_USER_DEFINED_FILENAME);
  }
}
