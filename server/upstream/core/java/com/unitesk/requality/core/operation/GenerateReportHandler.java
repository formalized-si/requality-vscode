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

package com.unitesk.requality.core.operation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.reference.RelationsTreeContext;
import com.unitesk.requality.core.exception.WorkspaceOperationPerforming;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.report.IReportGenerationParticipant;
import com.unitesk.requality.report.ReportGenerationContext;
import com.unitesk.requality.report.ReportGenerator;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.message.RMessages;

// TODO report undo
public class GenerateReportHandler implements IGenerateReportHandler {
  /*
   * (non-Javadoc)
   *
   * @see
   * com.unitesk.requality.core.operations.IGenerateReportHandler#execute(java
   * .util.List, java.util.Map)
   */
  public static Collection<ReportSettings> NO_REPORTS = Collections.emptyList();
  private static IGenerateReportHandlerProvider provider, 
    baseprovider = new IGenerateReportHandlerProvider() {
      @Override
      public IGenerateReportHandler getReportHandler() {
        return new GenerateReportHandler();
      }
    };
  public static IGenerateReportHandlerProvider getProvider() {
    if(provider == null) 
      provider = baseprovider;
    return provider;
  }
  public static IGenerateReportHandlerProvider getBaseProvider() {
    return baseprovider;
  }
  public static String ATTR_RELATED = "relatedSettings";
  //public ReportData lastReport = null;
  private String lastError = null;
  @Override
  public Object execute(boolean lockWorkspace, Collection<ReportSettings> nodes, Map<?, ?> par, IProgressMonitor monitor, boolean openAfter) throws Exception {
    if (nodes == null && par == null) return null;
    TreeDB db = null;
    if (nodes == null) {
      nodes = NO_REPORTS;
      ReportSettings t = (ReportSettings) par.get("target");
      if (t != null) {
        nodes = new LinkedList<ReportSettings>();
        nodes.add(t);
        db = t.getTreeDB();
      }
    }else
      for(ReportSettings rs:nodes)
        if(rs!=null){
          db = rs.getTreeDB();
          break;
        }
   // nodes.get(0).getTreeDB().startTransaction("Generate Reports");
    execute(lockWorkspace, db, monitor, openAfter, nodes.toArray(new ReportSettings[nodes.size()]));
    // nodes.get(0).getTreeDB().commit();
    return null;
  }

  public void openInformation(String title, String message) {
    RLogger.logInfo("I:" + title + ": " + message);
  }

  public void openError(String title, String message) {
    RLogger.logError("E:" + title + ": " + message);
  }

  private ReportGenResult<TreeNode>[] execute(boolean lockWorkspace, TreeDB baseDB, IProgressMonitor monitor,
      Set<TreeDB> was, boolean openAfter, ReportSettings ... reportS) throws WorkspaceOperationPerforming {
    if(lockWorkspace) {
      String wa = CoreUtils.getWorkspaceOperation();
      if(!wa.isEmpty())
        throw new WorkspaceOperationPerforming("Cannot generate report for "+Arrays.toString(reportS)+" because some operations("+wa+") are already performing on workspace");
      CoreUtils.lockWorkspace("Generate report for nodes "+Arrays.toString(reportS));
    }
    Set<TreeDB> startedTranslation = new HashSet<>();
    for(TreeDB db: was)
      if(db.startTransaction("Revertable transaction for "+db.getSemiProjectName()))
        startedTranslation.add(db);
    ReportGenResult<NodeDesc>[]descs = new ReportGenResult[reportS.length];
    Map<String,ReportGenerationContext>[] descMaps = new Map[reportS.length]; 
    try {
      int i = 0;
      for(ReportSettings rs : reportS) {
        Pair<Map<String, ReportGenerationContext>, ReportGenResult<NodeDesc>> retRes =
          generateReportBySettings(false, baseDB, rs, monitor, was, openAfter, null);
        descMaps[i] = retRes.getFirst();
        descs[i++] = retRes.getSecond();
      }
    }finally {
      if(lockWorkspace)
        CoreUtils.unlockWorkspace();
      for(TreeDB db:startedTranslation)
        db.rollback();
      
    }
    int i = 0;
    ReportGenResult<TreeNode>[]ret = new ReportGenResult[reportS.length];
    for(ReportGenResult<NodeDesc> n: descs)
    {
      TreeNode par = null;
      TreeDB targDB = null;
      for(TreeDB db:was) {
        par = db.getNode(n.reportParent);
        if(par!=null) {
          targDB = db;
          break;
        }
      }
      if(par!=null) {
        TreeNode old = par.findWorkChild(n.report.getId());
        if(old!=null)
          old.delete();
        TreeNode repNode = n.report.createNode(targDB, n.reportParent, true, true);
        ret[i] = new ReportGenResult<TreeNode>(n.href, n.reportParent, repNode, n.savePath);
        repNode.attrs().setConstAttribute("generationOver", ReportSettings.dataFormat.format(new Date()), 0);
        repNode.commitAttributes();
      }
    }
    for(int j = 0;i< descMaps.length;i++) {
      Map<String, ReportGenerationContext> s = descMaps[j];
      ReportGenResult<TreeNode> rgr = ret[j];
      boolean genRelated = ((ReportData)rgr.report).getCrossProjectFlag();
      for(ReportGenerationContext context2:s.values()) {
        boolean isSameDB = context2.baseDB==context2.db;
        if(isSameDB || genRelated) {
          context2.rootRData = (ReportData) rgr.report;//set report data to newer object
          if(isSameDB)
            context2.reportData = (ReportData) rgr.report;
          context2.doneProcess(null);
        }
      }
    }
	return ret;
  }

  @Override
  public ReportGenResult<TreeNode>[] execute(boolean lockWorkspace, TreeDB db, IProgressMonitor monitor, boolean openAfter, ReportSettings ... reportS) throws WorkspaceOperationPerforming {
	  RelationsTreeContext rtc = CoreUtils.gatherRelatedTrees(db, monitor, -1, null, true);
    Set<TreeDB> was = rtc.allRelated;
    return execute(lockWorkspace, db, monitor, was, openAfter, reportS);
  }
  /*
   * (non-Javadoc)
   *
   * @see com.unitesk.requality.core.operations.IGenerateReportHandler#
   * generateReport(com.unitesk.requality.nodetype.ReportSettings)
   */
//  public Pair<Map<String, ReportGenerationContext>, ReportGenResult<NodeDesc>> actualGenerateReport(boolean workspaceOp, ReportSettings reportS, IProgressMonitor monitor, boolean openAfter) throws WorkspaceOperationPerforming {
//    return generateReportBySettings(workspaceOp, reportS.getTreeDB(), reportS, monitor, new HashSet<>(), openAfter, null);
//  }

  SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
  private static int deep = 0;
  protected Pair<Map<String, ReportGenerationContext>, ReportGenResult<NodeDesc>> generateReportBySettings(boolean workspaceOp, TreeDB baseDB, ReportSettings reportS, IProgressMonitor monitor, Set<TreeDB>was, boolean openAfter, String saveTo) throws WorkspaceOperationPerforming {
    if(reportS == null)
      return null;
    if(reportS.isGenerating()) {
      lastError = "Some generation is already performing";
      throw new RuntimeException(lastError);
    }
    try {
      boolean wop = false;
      if(workspaceOp) {
        String wopa = CoreUtils.getWorkspaceOperation();
        if(!wopa.isEmpty()) {
          lastError = "Some workspace operation("+wop+") is already performing";
          throw new WorkspaceOperationPerforming(lastError);
        }
        CoreUtils.lockWorkspace("Generate report for settings "+reportS.getUserVisibleName());
        wop = true;
      }
  
      Requirement r = reportS.getRootTargetRequirement(baseDB);
      boolean tr = baseDB.inTransaction();
      if(!tr){
        RMessages.openError("Report generation error", "Report cannot be generate because transaction was not started for "+baseDB.getSemiProjectName(), baseDB.getStorage().getVisibleName(), tr);
        lastError = "Report cannot be generate because transaction was not started for "+baseDB.getSemiProjectName();
        throw new RuntimeException(lastError);
      }
      int ncnt = 0;

      File tempFold = null;
      if(saveTo == null) {
        try {
          tempFold = Files.createTempDirectory("requalityReport").toFile();
          if(!tempFold.exists())
          {
            tempFold.mkdirs();
          } 
          tempFold.deleteOnExit();
          saveTo = tempFold.toURI().toString();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      Map<String, ReportGenerationContext> contexts = new HashMap<>();
      try {
        TreeDB db = reportS.getTreeDB();
        String dbname = db.getProjectName();
        AtomicReference<RelationsTreeContext> related = new AtomicReference<>();
        CacheStorage cs = db.getCacheStorage();
        CacheTransactionDescription trans = cs.startCacheTransaction("Init report context "+db.getVisibleName(), 2);
        try{
          ReportGenerationContext.initGenerationContexts(this, null, db, db, reportS, null, monitor, contexts, null, related, null, null, null, null);
        }finally{
          if(trans != null && trans.error == null)
            cs.commit(trans);
        }
        
        ReportGenerationContext rootC = contexts.get(dbname);
        ReportData rdata = rootC==null?null:rootC.getReportData();
        if(rootC != null && rdata != null) {
          if(tempFold!=null)
            rootC.tempFold = tempFold;
          rootC.saveTo = saveTo;
          List<IReportGenerationParticipant> genParts = API.getReportGenerationParticipants();
          ReportGenerator.initMpartAndRelatedVariables(rootC);
          int i = 1;
          if(monitor!=null)
            monitor.beginTask("Participatns reaction "+rootC.db.getVisibleName()+" ["+i+"/"+related.get().allRelated.size()+"]", 1);
          try {
            for(IReportGenerationParticipant genpart:genParts) {
              genpart.generationContextIsReady(rootC);
            }
          }finally {
            if(monitor!=null)
              monitor.doneTask();
          }
          for(ReportGenerationContext context:contexts.values())
            if(context != rootC){
              i++;
              if(tempFold!=null)
                context.tempFold = tempFold;
              context.saveTo = saveTo;
              ReportGenerator.initMpartAndRelatedVariables(context);//init contexts for projects
              if(tempFold!=null)
                context.tempFold = tempFold;
              if(monitor!=null)
                monitor.beginTask("Participatns reaction "+context.db.getVisibleName()+" ["+i+"/"+related.get().allRelated.size()+"]", 1);
              try {
                for(IReportGenerationParticipant genpart:genParts)
                  genpart.generationContextIsReady(rootC);
              }finally {
                if(monitor!=null)
                  monitor.doneTask();
              }
            }

          String filename = TemplatesManager.getOne(db).getTplFileName(rdata, true, rootC);
          rootC.href =
              URI.create(
                rootC.savePath.toString()
                      + CoreUtils.URLEncode(
                          '/'+
                            filename));
          TemplatesManager.getOne(db).saveReportSettings(rdata, rootC.template, rootC);
            //report.attrs().setConstAttribute(ReportSettings.ATTR_TEMPLATE, tpl.getId(),Flag.HIDDEN);
          String val = new File(rootC.href).getName();
          rdata.attrs().setConstAttribute(ReportSettings.ATTR_LAST_GENERATED, val, Flag.HIDDEN);
          rootC.updateVariable(rdata.attrs().getWorkAttributeDefinition(ReportSettings.ATTR_LAST_GENERATED),
              val);
          rdata.commitAttributes();
          for(TreeDB dba: rootC.getRelatedTrees()) {
            ReportGenerationContext cont = rootC.getRelatedContext(dba);
            if(cont!=null) {
              if(cont.multiRoots!=null && cont.multiRoots.size()>0)
                cont.pageRootReq = (Requirement) cont.multiRoots.iterator().next();
              else
                cont.pageRootReq = (Requirement) cont.db.getNode(Requirement.getTypeRootQId());
            }
          }
          i = 1;
          try {
            if(monitor!=null)
              monitor.beginTask("Generate report for "+rootC.db.getVisibleName()+" ["+i+"/"+related.get().allRelated.size()+"]", 1);
            try {
              ReportGenerator.generateReport(rootC);
            }finally {
              if(monitor!=null)
                monitor.doneTask();
            }
          } catch (Exception e) {
             rootC.lastError = "Error during generation: "+CoreUtils.getTrace(e);
             RLogger.logError(e);
          }

          boolean genRelated = reportS.getCrossProjectFlag();
          for(TreeDB dba:related.get().allRelated){
            String dbnamea = dba.getProjectName();
            if(!dbname.equals(dbnamea)) {
              i++;
              if(monitor!=null)
                monitor.beginTask("Generate report for "+dba.getVisibleName()+" ["+i+"/"+related.get().allRelated.size()+"]", 1);
              try {
                ReportGenerationContext context2 = contexts.get(dbnamea);
                if(context2!=null && (context2.db == context2.baseDB/*seems to be impossible here*/ || genRelated))
                  try {
                   ReportGenerator.generateReport(context2);
                  } catch (Exception e) {
                    if(context2!=null)
                      context2.lastError = "Error during generation: "+CoreUtils.getTrace(e);
                    RLogger.logError(e);
                  }
              }finally {
                if(monitor!=null)
                  monitor.doneTask();
              }
            }
          }
          //TODO more contexts?
          String nextUid = rdata.attrs().getStringAttributeValue(ReportData.NEXT_REPORT_UUID, UUID.randomUUID().toString());
          NodeDesc nd = NodeDesc.fromNode(rdata, UUID.fromString(nextUid), true);
          return new Pair<>(contexts, new ReportGenResult<NodeDesc>(rootC.href, rdata.getWorkParentUUID(), nd, rootC.savePath));
        }else {
          return null;
        }
      }finally {
        if(wop)
          CoreUtils.unlockWorkspace();
      }
    }catch(Exception e){
      if(reportS.isGenerating()) {
        reportS.setGenerating(false);
        reportS.commitAttributes();
      }
      throw e;
    }
  }

  private void saveTempFilesToReport(ReportSettings reportS, File tempFold) {
  saveTempFilesToReport(reportS, tempFold, "");
  }
  private void saveTempFilesToReport(ReportSettings reportS, File tempFold, String path) {
  File[] fls = tempFold.listFiles();
  for(File f:fls){
    if(f.isFile())
      try(FileInputStream fis = new FileInputStream(f)){ 
       reportS.setResourceContent(path+f.getName(), fis, OFlag.NONE);
      } catch (FileNotFoundException e) {
          e.printStackTrace();
      } catch (IOException e) {
          //TODO Auto-generated catch block
      e.printStackTrace();
        }
      else {
        String apath = path.isEmpty()?f.getName():path+"/"+f.getName();
        saveTempFilesToReport(reportS, f, apath);
      }
    f.delete();
    }
  }


  private void recCopy(File rf, File f) {
    try{
      for(File f2:rf.listFiles()){
        File nf = (f.toPath().resolve(f2.getName())).toFile();
        if(f2.isDirectory()){
          nf.mkdirs();//TODO may cause exc
          recCopy(f2, nf);
        }else
          try {
            Files.copy(f2.toPath(), nf.toPath(), StandardCopyOption.REPLACE_EXISTING);
          } catch (IOException e) {
            // TODO Auto-generated catch block
            RLogger.logError(e);
          }
      }
    }catch(Exception e){
      RLogger.logInfo("Exception on rec copy "+e.getMessage()+" "+CoreUtils.getTrace(e.getStackTrace()));
    }
  }


  public void openReport(ReportData res) {
    // OpenReportHandler.openReport(report);
    // TODO some open
  }

  public static Template getTemplate(ReportSettings report) {
    if(report == null)
      return TemplatesManager.getOne(null).get(Template.DEFAULT_ID);
    String templateId = report.getTemplateId();
    TreeDB db = report.getTreeDB();
    Template tpl = null;
    if (templateId == null) {
      tpl = TemplatesManager.getOne(db).get(Template.DEFAULT_ID);
      TemplatesManager.getOne(db).saveReportSettings(report, tpl, null);
     // report.attrs().setConstAttribute(ReportSettings.ATTR_TEMPLATE, tpl.getId(),
       //   Flag.HIDDEN);
      report.commitAttributes();
    } else {
      tpl = TemplatesManager.getOne(db).get(templateId);
    }
    return tpl;
  }

  public static Template getTemplate(TreeDB db, String tid) {
    return TemplatesManager.getOne(db).get(tid);
  }

  // remove old image from report before new generation
  @Override
  public void clearOldImages(ReportSettings nde) {
  //Now seems to be outdated
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * com.unitesk.requality.core.operations.IGenerateReportHandler#isEnabled(
   * java.util.List<Object>)
   */
  @Override
  public boolean isEnabled(boolean lockWorkspace, Collection<ReportSettings> nodes) {
    if (nodes == null) return false;
    TreeDB chd = null;
    if(lockWorkspace) {
      String op = CoreUtils.getWorkspaceOperation();
      if(!op.isEmpty()) {
        RLogger.logDebug("Cannot generate report on "+Arrays.toString(nodes.toArray())+" because "+op+" is performing");
        return false;
      }
    }
    TreeStateManager tsm = null;
    for (TreeNode node : nodes) {
      if (!node.getType().equals(ReportSettings.TYPE_NAME)) return false;
      if(chd == null || node.getTreeDB()!=chd) {
        chd = node.getTreeDB();
        if(tsm == null)
          tsm = TreeStateManager.getOne(chd.getTracker());
        if(!tsm.canStartTransaction(chd.getLocalName(), tsm.getRevisionInfo(chd)))
          return false;
        }
        ReportSettings rs = (ReportSettings) node;
        TemplatesManager tm = TemplatesManager.getOne(node.getTreeDB());
        Template t = tm.getTemplate(rs.getTemplateId());
        if(t == null || t.getNotExists())
          return false;
    }
    return true;
  }


  public static void init(IGenerateReportHandlerProvider g) {
    getProvider();//init base
    provider = g;
  }

  public static IGenerateReportHandler getOne() {
    return getProvider().getReportHandler();
  }

  @Override
  public String getLastError() {
    return lastError;
  }
}
