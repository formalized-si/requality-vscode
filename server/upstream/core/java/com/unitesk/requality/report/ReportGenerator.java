/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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
package com.unitesk.requality.report;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.MultiPart;
import com.unitesk.requality.core.SharedConsts;
import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.localization.Localization;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.IDMechanics;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.GenerateReportHandler;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.repo.RepoManager;
import com.unitesk.requality.repo.RepoTracker;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.template.IReportDataProvider;
import com.unitesk.requality.report.template.IReportDataSource;
import com.unitesk.requality.report.template.IReportSettingsConverter;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateFile;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tests.CoverageFromFile;
import com.unitesk.requality.tests.CoverageStorage;
import com.unitesk.requality.tests.ICoverageStorageSource;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.RequalityCLI;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;

public class ReportGenerator {
  //private static boolean useStat;
  private static Set<String> notPropogatedAttributes = new HashSet<>();
  static {
	//TODO disable propogation for default report-related variables(without date)
    notPropogatedAttributes.add("isHTML");
    notPropogatedAttributes.add("revisionCur");
    notPropogatedAttributes.add("revisionPrev");
    notPropogatedAttributes.add("noAddTestButtons");
    notPropogatedAttributes.add("coverageFilePath");
    notPropogatedAttributes.add("coverageStorageType");
    notPropogatedAttributes.add("coverageStorageSettings");
    notPropogatedAttributes.add("testIdFormula");
    notPropogatedAttributes.add("selFormula");
    notPropogatedAttributes.add("selAttrsWidth");
    notPropogatedAttributes.add("selAttrs");
    notPropogatedAttributes.add("dateFrom");
    notPropogatedAttributes.add("dateTo");
    notPropogatedAttributes.add("step");
    notPropogatedAttributes.add("filter");
    notPropogatedAttributes.add("attrFilter");
    notPropogatedAttributes.add("toggleEnabled");
    notPropogatedAttributes.add("traceabilitySettings");
    notPropogatedAttributes.add("useProjectNumeration");
    notPropogatedAttributes.add("simplifiedAttrs");
    
  }
  public static void clearOldImages(File f, String saveTo) {
    // TODO look
    if (!f.exists()) return;
    String fc = RequalityCLI.getFileContent(f);
    RequalityCLI.deleteOldImages(fc, null, null, saveTo);
  }

  private static void hideInstancers(TreeNode node, ReportGenerationContext context, List<TreeNode> hidden) {
    // TreeDB root = VirtualNode.getRootTree(node.getTreeDB(),3);
    if (node == null || context.monitor.isCanceled()) return;
    context.monitor.worked(1);
    TreeDB root = node.getTreeDB(); // TODO root?
    if (!root.isVirtual()) {
      String uid = node.getVRoot();
      if (uid != null) root = Instantiator.getExistVirtualTreeDB(root, uid);
        if(root.isVirtual())
          root = Instantiator.getRootTree(root); 
    }
    node = root.getNode(node.getUUId());
    if (node == null || node.getType() == null) 
      RLogger.logDebug("NF!");
    if(context.monitor.isCanceled())
      return;
    if (node!=null && node.getType().equals(VirtualNode.TYPE_NAME) && !(((VirtualNode) node).isCollapsed())) {
      ((VirtualNode) node).collapse();
      hidden.add(node);
      node.commitAttributes();
    }
    if(context.monitor.isCanceled())
      return;
    if (node != null)
      for (UUID node2 : node.getWorkChildrenUUIDs())
        hideInstancers(node.getTreeDB().getNode(node2), context, hidden);
  }

  public static void generateReport(
      ReportGenerationContext context)
      throws Exception {
    List<TreeNode> roots = context.multiRoots;
    if(context.getReportData()==null)
      return;
    for(TreeNode node: roots) {
      //context.targetReq = (Requirement) node;//TODO test
      TreeDB db = node.getTreeDB();
      ReportGenerationContext cont = context.getRelatedContext(db);
      if(cont == null)
        cont = context;

      if(cont.getReportData()==null)
        continue;
      cont.pageRootReq = node;
      Map<String, Attribute> oldAttrs = new HashMap<>();
      //TODO not requirement?
      if(db.revision == RevisionInfo.NO_REV || db.revision == null)
        for(Entry<String, AttributeDefinition> en: context.getFullReqsPropogateData().entrySet()){
          AttributeDefinition ad = en.getValue();
          if(!notPropogatedAttributes.contains(en.getKey())&&!en.getValue().isHidden()) {
            Attribute old = node.attrs().setAttribute(new AttributeDefinition(node.getUUId(),
              ad.getKey(), AttributeAvailability.SUBTREE, ad.getValueProvider(),ad.getFlags()));
            oldAttrs.put(ad.getKey(), old);
          }
        }
      node.commitAttributes();
      try {
        Configuration cfg = new Configuration();
        cfg.setObjectWrapper(new DefaultObjectWrapper());
  
        File f = new File(URI.create(cont.template.getReportPath()));
        cfg.setDirectoryForTemplateLoading(f.getParentFile());
      
        File targetFile = new File(URI.create(cont.saveTo));
        targetFile.mkdirs();
        for (TemplateFile t : cont.template.getTemplates()) {
          cont.uriResolver.setTemplateFile(t);
          String tFileName = t.getOutputFileName(
        		  cont.getReportData().getUserDefinedFileName(t),
              node.getTreeDB(), node, cont.getReportData());
          File templTarget = targetFile.toPath().resolve(tFileName).toFile();
          if(templTarget.exists())
          {
            RLogger.logDebug("Report file already exists, skipped "+tFileName+" for "+node.getTreeDB().getProjectName()+"("+node.getUserVisibleName()+")");
            continue;
          }
          try (FileOutputStream fw = new FileOutputStream(templTarget)) {
            //try(OutputStreamWriter out = new OutputStreamWriter(fw, StandardCharsets.UTF_8)){
              
              Map<String, Object> data = cont.getFullReportData(node, t);
              
              IReportTemplateProcessor ftl = detectProcessor(t.getType(), cfg, f.getParentFile(), f.getName()+"/"+t.getTemplatePath());
  //            freemarker.template.Template ftl = cfg.getTemplate(f.getName()+"/"+t.getTemplatePath(), "UTF-8");
              // Statistics st = null;
              // if(cs.getSettings()!=null){
              // st = new
              // Statistics(rootReq.getTreeDB().getRootNode().getResourcesPath());
              // }
              data.put("context", cont);
              data.put("coreUtils", new CoreUtils());
              data.put("locale", Localization.getLocale());
              data.put("lang", Localization.getLanguage());
              data.put("localization", Localization.getOne());
              data.put("pageRootReq", context.pageRootReq);
              data.put("rootReq", context.reportRootReq);
              if(!data.containsKey("ID_PREFIX"))
                data.put("ID_PREFIX", IDMechanics.getShownIdPrefix(cont.getTree()));
              if(ftl != null)
                ftl.process(data, node, cont, data, fw);
              //ftl.process(data, out);
              // TODO fix for report treeNode
      
              if (cont.coverageStorage.getSettings(node) != null && !cont.coverageStorage.isEmpty())
                cont.coverageStorage.saveCoverageToFile(
                    cont.saveTo
                        + "/"
                        + CoreUtils.URLEncode(tFileName)
                        + "_lastCov.xml");
            //} 
          }catch (Exception e) {
            RLogger.logError(CoreUtils.getTrace(e));
            throw e;
          } finally {
            ReportOutProcessor.shelduleReportUpdate(node, tFileName, templTarget, null, null, null);
            if(cfg!=null) {
              cfg.clearSharedVariables();
              cfg.clearEncodingMap();
              cfg.clearTemplateCache();
            }
          }  
        }
      }finally {
        if(!oldAttrs.isEmpty()) {
          for(Entry<String, Attribute> en: oldAttrs.entrySet())
            if(en.getValue() == null)
              node.attrs().removeAttribute(en.getKey());
            else
              node.attrs().setAttribute(en.getValue().getDefinition());
          node.commitAttributes();
        }
      }
    }
  }

  public static void initMpartAndRelatedVariables(ReportGenerationContext context) {
    if(context.saveTo == null){
      //init temp folder for it
      try {
        context.tempFold = Files.createTempDirectory("requalityReport").toFile();
        context.saveTo = context.tempFold.toURI().toString();
        if(!context.tempFold.exists())
        {
          context.tempFold.mkdirs();
        }
        context.tempFold.deleteOnExit();
      } catch (IOException e) {
        RLogger.logError(e);
      }
    }
    
    Map<String, ReportUtils> treeReportUtils = new HashMap<>();    
    //init generic non-root utils and project-level mpart setts
    Requirement initialRootReq = context.reportRootReq;
    ReportData report = context.getReportData();
    Template templ = context.template;
    if(initialRootReq == null)
      initialRootReq = (Requirement) context.db.getNode(Requirement.getTypeRootQId()); 
    ReportUtils genericUtils = new ReportUtils(
            context.baseDB,
            context.db, 
            initialRootReq, report, templ.getTemplates().iterator().next(), templ.getTemplates(), 
            context);//TODO select proper file?
    if(!context.reportDataInited)
    {
      for(Entry<String, Attribute> en: report.attrs().getAvailableAttributes().entrySet())
        if(!context.reportParameters.containsKey(en.getKey()) && en.getValue()!=null && en.getValue().isUserVisible())
          context.reportParameters.put(en.getKey(), en.getValue().getValue());
      context.reportDataInited = true;
    }
    Map<String, Object> rd = context.reportParameters;
    Map<String, AttributeDefinition> pd = new HashMap<>();
    //adds files for propogation
    if(context.db!=null && context.baseDB != context.db){
      IReportSettingsConverter rsc = context.template.getCPConvertClass();
      if(rsc!=null){
        rd = rsc.getUpdatedSettings(context, rd, context.baseDB, context.db);
        pd = rsc.getUpdatedDefinitionSettings(context, pd, context.baseDB, context.db);
      }
    }
    context.reportParameters = rd;//seems to set proper values for additional contexts 
    context.mpart = templ.getMultiPartProvider(initialRootReq, genericUtils, 
        context, initialRootReq.getTreeDB().getMultiPart(initialRootReq, genericUtils, context));
    
    if (initialRootReq == null) throw new RuntimeException("Root node is null!");
    List<TreeNode> hidden = new ArrayList<>();
    if(context.monitor!=null) 
      context.monitor.beginTask("hide instances "+context.baseDB.getVisibleName(), context.nodesCount);
    try{
      hideInstancers(initialRootReq, context, hidden);//now will be reverted on the end automatically
    }finally {
      if(context.monitor!=null) 
        context.monitor.doneTask();
    }
    boolean multipart = templ.getSupportsMultiPartMode() && context.mpart.isEnabled(initialRootReq);
    
    
    context.reportParameters = rd;
    context.reportPropData = pd;
    Map<String, Object> reportData = context.reportParameters;
    
    if(context.monitor!=null)
      context.monitor.beginTask("init mpart data for "+context.baseDB.getVisibleName(), 1);
    try{
      MultiPart mp = templ.getMultiPartProvider(initialRootReq, genericUtils, context, context.mpart);
      tryAddLinksProvider(mp, genericUtils.getTemplateFile(), context.dbLinksReplace);
      //update vars?
            
      //multipart settings init
      if(templ.getSupportsMultiPartMode())
        context.multiRoots = context.mpart.getMultipartRoots(context, initialRootReq, report!=null?report.getCrossProjectFlag():false);
      else
        context.multiRoots = Arrays.asList(new TreeNode[]{initialRootReq});
      CoverageStorage cs = null;
      if (reportData.containsKey("coverageStorageType")) {
        String type = (String) reportData.get("coverageStorageType"),
            setts = (String) reportData.get("coverageStorageSettings");
        ICoverageStorageSource ics = API.getCoverageSource(type);
        if (ics != null) {
          cs = CoverageStorage.getStorage(initialRootReq.getTreeDB(), ics);
          if (setts != null) cs.setSettings(setts.split(","));
        } else cs = CoverageStorage.getStorage(initialRootReq.getTreeDB(), new CoverageFromFile());
      } else {
        cs = CoverageStorage.getStorage(initialRootReq.getTreeDB(), new CoverageFromFile());
        if (reportData.containsKey("coverageFilePath"))
          cs.setSettings(new String[] {(String) reportData.get("coverageFilePath")});
      }
      context.coverageStorage = cs;
      context.reportParameters.put("coverage", cs);//TODO test
      context.reportParameters.put("multiPartMode", multipart);
      context.reportParameters.put("context", context);
      context.reportParameters.put("multiPartRoots", context.multiRoots);
      context.reportParameters.put("project_local_name", context.db.getLocalName());
      context.reportParameters.put("project_name", context.db.getProjectName());
      context.reportParameters.put("reportLocation", context.saveTo);
      context.reportParameters.put("root", context.db.getRootNode());
      
      Progress st =
          new Progress(context.db.getRootNode().getNodeResourcesPath(), context.saveTo);
      context.reportParameters.put("progress", st);
      if (templ.getUsesStatistics()) {
        Class<? extends Object> c = SharedConsts.statManClass;
        if (c != null) {
          TreeDB db = context.db;
          if (db.isVirtual()) db = Instantiator.getRootTree(db);
          Object sm;
          try {
            sm = c.getConstructor(CoverageStorage.class, TreeDB.class, URI.class, ReportGenerationContext.class)
                .newInstance(cs, db, context.db.getRootNode().getNodeResourcesPath(), context);
            context.reportParameters.put("statman", sm);
          } catch (Exception e) {
            RLogger.logError(e);
          }
        }
      } else {
        StatisticManagerCLI sm = new StatisticManagerCLI(context.pageRootReq, cs);//TODO mpart?
        context.reportParameters.put("statman", sm);
      }
      
      if(context.dbLinksReplace == null)
        context.dbLinksReplace = new HashMap<>();
      context.nodeUtils = new HashMap<>();
      for(TreeNode node: context.multiRoots){
        if(node == null)
          continue;
        Requirement rootReq = context.reportRootReq;
        TreeNode pageRoot = context.pageRootReq;
        
        Map<String, Object> data = new HashMap<>();
        Map<TemplateFile,Map<String, Object>> nodeData = new HashMap<>();
        context.nodeUtils.put(node.getUUId(), nodeData);
    //    data.putAll(rootdata); //preinited data objects
        data.put("multiPartRoot", node);
        
        data.put("relMan", new RelManager());
        data.put("rootReq", rootReq);
        data.put("pageRootReq", pageRoot);

        putClassesForChecks(data);
        for (TemplateFile t : templ.getTemplates()) {
          Map<String, Object> data2 = new HashMap<>(data);
          nodeData.put(t, data2);
          context.uriResolver.setTemplateFile(t);
          
          ReportUtils ru = new ReportUtils(
            context.baseDB, rootReq.getTreeDB(),
            rootReq, report, t,
            templ.getTemplates(), context);
          
          data2.put("utils", ru);
          if(context.sources == null){
            context.sources = new HashMap<>();
            context.sourcesData = new HashMap<>();
            context.sourcesDataInsts = new HashMap<>();
            for(Entry<String, IReportDataProvider> en:templ.getDataSources().entrySet()){
              IReportDataProvider prov = en.getValue();
              Object newDataProv = context.sourcesDataInsts.get(prov); 
              if(newDataProv == null){
                newDataProv = prov.getDataSource(initialRootReq, ru, context);
                context.sourcesDataInsts.put(prov, newDataProv);
              }
              context.sources.put(en.getKey(), prov);
              
              tryAddLinksProvider(en.getValue(), t, context.dbLinksReplace);
              tryAddLinksProvider(newDataProv, t, context.dbLinksReplace);
              if(newDataProv instanceof IMultiPartTreeLinksProvider){
                IMultiPartTreeLinksProvider prov2 = (IMultiPartTreeLinksProvider) newDataProv;
                prov2.setMultiPartLinksMapObject(t, context.dbLinksReplace);
              }
              context.sourcesData.put(en.getKey(), newDataProv);
            }
          }else
          {
            for(Entry<String, IReportDataProvider> en:context.sources.entrySet())
              en.getValue().updateDataSource(context.sourcesData.get(en.getKey()), ru, context,initialRootReq);
          }
          
          for(Entry<String, Object> en:context.sourcesData.entrySet()){
            data2.put(en.getKey(),
                en.getValue());
          }
           
          if (t.getDataClass().length() > 0) {
            try{
              IReportDataSource repObject = t.instanceDataObject(context.baseCL);
              tryAddLinksProvider(repObject, t, context.dbLinksReplace);
              data2.putAll(repObject.getData());
              data2.put("report", repObject);
            }catch(Exception e){
              RLogger.logError(e);
            }
          } else {
            data2.put("report", new ReportTools());
          }

        }
        
      }
    }finally{
      if(context.monitor!=null)
        context.monitor.doneTask();
    }
       
  }

  private static void putClassesForChecks(Map<String, Object> data) {
    data.put("ReferenceValue", ReferenceValue.class);
  }
  
  private static IReportTemplateProcessor detectProcessor(String type,
      Configuration cfg, File templParent, String templFile) throws Exception {
    if(type == null || type.toLowerCase().equals("ftl"))
      return new IReportTemplateProcessor() {
        
        @Override
        public void process(Map<String, Object> setts, TreeNode node, ReportGenerationContext reportGenHelper, Map<String, Object> data, OutputStream out) throws Exception {
          OutputStreamWriter osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
          try{
            try {
              freemarker.template.Template templ = cfg.getTemplate(templFile, "UTF-8");
              templ.process(data, osw);
            }catch(Exception e) {
              osw.write(CoreUtils.getTrace(e));
              throw e;
            }
          }finally{
            osw.close();
          }  
        }

      }; 
    return API.getReportTemplateProcessor(type.toLowerCase(),templParent.toPath().resolve(templFile).toFile());
  }

  private static void tryAddLinksProvider(
      Object mp,TemplateFile tf, Map<TemplateFile, Map<String, String>> dbLinksReplace) {
    if(mp instanceof IMultiPartTreeLinksProvider){
      IMultiPartTreeLinksProvider prov = (IMultiPartTreeLinksProvider) mp;
      prov.setMultiPartLinksMapObject(tf, dbLinksReplace);
    }
  }
  
  private static ReportSettings createTemporaryReportSettings(
      TreeDB db, Map<String, Object> userData, Template templ) {
    TreeNode insts = db.getNode("/Instances");
    if(insts == null){
      insts = db.getRootNode().createChild("Instances");
    }
    
    ReportSettings rs = (ReportSettings) insts.createChild(UUID.randomUUID()+templ.getId(), ReportSettings.TYPE_NAME);
    rs.setCrossProjectFlag(false);
    rs.setGenerating(false);
    TemplatesManager.getOne(db).saveReportSettings(rs, templ, null);
    for(Entry<String, Object> s:userData.entrySet()){
      com.unitesk.requality.core.attribute.AttributeType type = 
          com.unitesk.requality.core.attribute.AttributeType.detectType(s.getValue());
      rs.attrs().setConstAttribute(type, s.getKey(), s.getValue(), 0);
    }
    rs.commitAttributes();
    return rs;
  }

  public static void generateReport(
      Requirement rootReq, Template templ, String saveTo, Map<String, Object> userData,
      IProgressMonitor mon)
      throws Exception {
    TreeDB rootTree = rootReq == null ? null : rootReq.getTreeDB();
    RepoManager rm = Global.getRepoManager(rootTree);
    RepoTracker rt = null;
    if (rm != null) {
      rt = rm.getRepoTracker();
    } else {
      if(rootTree != null)
        rt = RepoTracker.getRepoTracker(rootTree.getTracker());
    }
    if(rt!=null)
      rt.resetSlice(mon);
    
    ReportSettings reportS = createTemporaryReportSettings(rootTree, userData, templ);
    GenerateReportHandler.getOne().execute(true, 
        Arrays.asList(new ReportSettings[]{reportS})
        , userData, mon, true);
    
    if(rt!=null){
      rt.resetSlice(mon);
      rt.deleteMarkedSlices();
    }
    
  }

}
