package com.unitesk.requality.report;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.unitesk.requality.core.MultiPart;
import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.SystemNames;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.attribute.reference.RelationsTreeContext;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.IDMechanics;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.GenerateReportHandler;
import com.unitesk.requality.core.operation.IGenerateReportHandler;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.IReportStateListener;
import com.unitesk.requality.core.storage.DBLocalName;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.nodetype.InstancesFolder;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.repo.DBSlice;
import com.unitesk.requality.repo.DummyReportNodeCacheListener;
import com.unitesk.requality.repo.RepoTracker;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.template.IReportDataProvider;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateFile;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tests.CoverageStorage;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.message.RMessages;

public class ReportGenerationContext {
  public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

  public IResTracker baseTracker = null;
  public TreeDB baseDB;
  public TreeDB db;
  public ReportSettings reportS;
  public IProgressMonitor monitor;
  public boolean generationStarted = false;
  public boolean needsReportSFlagReset = false;
  public URI href;
  public URI savePath;
  public int nodesCount;
  public DummyReportNodeCacheListener addedReportListener;
  public ReportData reportData;
  public ReportData rootRData;
  public Template template;
  public String date;
  public TreeNode pageRootReq;//target requirement(mroot)
  public Requirement reportRootReq;//root selected requirement on project
  public DefaultReportURIResolver uriResolver = null;
  
  public MultiPart mpart = null;
  public String saveTo;
  public Set<TreeNode> nodesToRemove;
  public Map<UUID, Map<TemplateFile,Map<String, Object>>> nodeUtils;
  public ClassLoader baseCL;
  public CoverageStorage coverageStorage;
  public File tempFold = null;
  public Map<TemplateFile, Map<String, String>> dbLinksReplace = null;
  public Map<String, DBSlice> storedSlices = new HashMap<>();
  public HashMap<String, IReportDataProvider> sources = null;
  public HashMap<String, Object> sourcesData = null;
  public HashMap<IReportDataProvider, Object> sourcesDataInsts = null;
  public HashMap<String, ReportGenerationContext> relatedContexts = null;
  public List<TreeNode> multiRoots = null;
  public Map<String, Object> reportParameters = null;
  Map<String, AttributeDefinition> reportPropData = null;
  private boolean reportPropDataInitedOnReport = false;
  public Set<TreeDB> relatedTrees = null;
  public String lastError = null;

  public RelationsTreeContext relatedTreesContext;

  boolean reportDataInited = false;
  //TODO now they are separate, check it
  private List<IReportStateListener> statusListeners = new LinkedList<>();
  public List<IReportStateListener> getStatusListeners() {
    return statusListeners;
  }
  public void registerStatusListener(IReportStateListener rsl) {
    statusListeners.add(rsl);
  }
  public void unregisterStatusListener(IReportStateListener rsl) {
    statusListeners.remove(rsl);
  }
  
  public ReportGenerationContext(
      TreeDB base, TreeDB db2, ReportSettings reportS2, IProgressMonitor monitor2, boolean b) {
    this.baseDB = base;
    baseTracker = base.getTracker();
    this.db = db2;
    this.reportS = reportS2;
    this.monitor = monitor2;
    this.needsReportSFlagReset  = b;
  }
  public static void initGenerationContexts(
      GenerateReportHandler handler, String bdate,
      TreeDB baseDB,
      TreeDB db,
      ReportSettings reportS,
      ReportData rootRData,
      IProgressMonitor monitor,
      Map<String, ReportGenerationContext> contexts,
      Template tpl,
      AtomicReference<RelationsTreeContext> relDBs,
      Map<TemplateFile, Map<String, String>> dbLinksReplace,
      HashMap<String, ReportGenerationContext> relatedContexts,
      Map<String, DBSlice> storedSlices,
      DefaultReportURIResolver uriResolver) {
    if(db == null)
      db = reportS.getTreeDB();
    String ldbname = db.getLocalName();
    String dbname = db.getProjectName();
    ClassLoader cl = ClassLoader.getSystemClassLoader();
    
    ReportGenerationContext rgc = contexts.get(dbname);
    IGenerateReportHandler grh = GenerateReportHandler.getOne();
    if(rgc != null && rgc.generationStarted)
      return;//skip this

    monitor.beginTask("init template - "+db.getProjectName(), 1);
    if(tpl == null){
      String tid = reportS.getTemplateId();
      tpl = GenerateReportHandler.getTemplate(db, tid);
      try{
        if (tpl == null) {
          if (tid != null) {
            grh.openError(
              "Report generation",
              "Report template '" + reportS.getTemplateId() + "' for this report is not avalible.");
            if(rgc!=null)
              rgc.lastError = "Report template '" + reportS.getTemplateId() + "' for this report is not avalible.";
            return;
          }        
          tpl = TemplatesManager.getOne(db).get(Template.DEFAULT_ID);
        }
      }finally{
        monitor.doneTask();
      }
    }
    if(tpl == null)
      throw new RuntimeException("Target templage does not exist for node "+reportS==null?"NONE":reportS.getUserVisibleName());
    if(reportS!=null) {
      if(reportS.isGenerating())
        throw new RuntimeException("Some generation is already performing for settings "+reportS.getUserVisibleName());
      reportS.setGenerating(true);
      reportS.commitAttributes();
    }
    
    //init monitor if not
    int max = 100000;
    int rest = monitor.getRest();
    monitor.beginTask("init generation context for "+db.getProjectName()+" on "+(reportS!=null?reportS.getSUUId():"NONE"), max);
    
    rgc = new ReportGenerationContext(baseDB, db, reportS, monitor, true);
    if(storedSlices==null)
      storedSlices = new HashMap<>();
    rgc.storedSlices = storedSlices;
    if(reportS!=null) {
      rgc.reportDataInited  = true;
      rgc.reportParameters = reportS.getData();
    }else
      rgc.reportParameters = new HashMap<>();
    

    if(tpl!=null) {
      Map<String, Object> params = tpl.getDefaultParamsValues();
      for(Entry<String, Object> en: params.entrySet())
        if(!rgc.reportParameters.containsKey(en.getKey())) 
          rgc.reportParameters.put(en.getKey(), en.getValue());
    }
    if(uriResolver!=null)
      rgc.uriResolver = uriResolver;
    else{
      rgc.uriResolver = new DefaultReportURIResolver(rgc, null);
          
    }
    rgc.baseCL = cl;
    rgc.template = tpl;
    
    if(baseDB == db)
      rgc.dbLinksReplace = new HashMap<>();
    else
      rgc.dbLinksReplace = dbLinksReplace;
    if(relatedContexts != null)
      rgc.relatedContexts = relatedContexts;
    else
      rgc.relatedContexts = new HashMap<>();
    rgc.relatedContexts.put(ldbname, rgc);
    contexts.put(dbname, rgc);//start init
    
    monitor.doneTask();
    
    monitor.beginTask("clear old slices for "+db.getProjectName()+" on "+(reportS!=null?reportS.getSUUId():"NONE"), 1);
    try{
      RepoTracker rt = RepoTracker.getRepoTracker(db.getTracker());
      rt.deleteMarkedSlices();
    }finally{
      monitor.doneTask();
    }

    //create rdata object
    NodeDesc rs = null;
    String date = bdate;
    if(date == null)
      date = new Date(System.currentTimeMillis()).toString();
      
    rgc.date = date;
    boolean genLinked = false;
    String timeStamp = "(" + DATE_FORMAT.format(new Date(System.currentTimeMillis())) + ")";
    if(reportS!=null) {
      genLinked = reportS.getCrossProjectFlag();
      rs = NodeDesc.fromNode(reportS, true);
      rs.setId(rs.getId()+timeStamp);
      rs.setAttribute(new AttributeDefinition(rs.getUUId(), 
        TreeNode.ATTR_TYPE, AttributeAvailability.LOCAL,new ConstValueProvider(AttributeType.STRING, ReportData.TYPE_NAME),
        Flag.HIDDEN));
      rs.setAttribute(new AttributeDefinition(rs.getUUId(),
        ReportSettings.ATTR_DATE, AttributeType.STRING, date));
    }
    WriteLock wl = null;
    //Map<String, ReportGenerationContext> contexts = new HashMap<>();
    try {
      // nd.setId((reportS.getId()+" ["+tpl.getName()+"]
      // ("+date+")").replace(' ', '_').replace(':', '-'));
      monitor.beginTask("init rdata node for report generation in "+db.getProjectName(), 1);
      if(reportS!=null && rs!=null) {
        rgc.reportData = (ReportData) rs.createNode(db, reportS.getWorkParentUUID(), true, false);
        for(Entry<String, Object> en: rgc.reportData.getData().entrySet()){
        if(!rgc.reportParameters.containsKey(en.getKey()))
          rgc.reportParameters.put(en.getKey(), en.getValue());
        }
      }
      if(baseDB == db) {
        rgc.rootRData = rgc.reportData;
        if(rgc.reportData == null)
          rgc.reportData = rootRData;
      }else {
        rgc.rootRData = rootRData;
        if(rgc.reportData == null)
          rgc.reportData = rootRData;
      }
      if(handler.lastReport == null)
        handler.lastReport = rgc.reportData;
      
    }finally{
      monitor.doneTask();      
    }

    monitor.beginTask("init treedb settings - "+db.getProjectName(), 1);
    
    monitor.doneTask();
    monitor.beginTask("init related dbs - "+db.getProjectName(), 1);
    rgc.nodesCount = db.getStorage().getUCM().getNodesCount();//number of reqs in tree
    if(relDBs == null){
      relDBs = new AtomicReference<>();
      //bad case, this info will be lost
    }
    RelationsTreeContext relatedDBs = relDBs.get();
    if(relatedDBs==null) {
      rgc.relatedTreesContext = CoreUtils.gatherRelatedTrees(db, monitor, -1, null, true);
      String pname = db.getProjectName();
      if(!rgc.relatedTreesContext.allRelated.contains(db)) {//TODO test, it can be already added
        rgc.relatedTreesContext.registerName(
           DBLocalName.formLocalNameKey(db, db.getLocalName(), db.revision),
           pname);
        rgc.relatedTreesContext.allRelated.add(db);
        rgc.relatedTreesContext.allRelatedNames.add(pname);
      }else
        RLogger.logDebug("init context, tries to add already added db "+db.getLocalName()+" "+RevisionInfo.toString(db, db.revision));
      rgc.relatedTrees = rgc.relatedTreesContext.allRelated;
    }else {
      rgc.relatedTreesContext = relatedDBs;
      rgc.relatedTrees = relatedDBs.allRelated;
    }
    relDBs.set(rgc.relatedTreesContext);
    //TODO init ref dbs
    if(relDBs != null && rs != null)
    {
      File rf = null;
      if (rgc.reportData != null)
        rf = rgc.reportData.getNodeResourcesFile();
      for(TreeDB db2:relDBs.get().allRelated)
      {
        String db2name = db2.getProjectName();
        if(contexts.containsKey(db2name))
          continue;
        TreeNode cr = db2.getNode("Instances");
        boolean crAdded = false;
        if(genLinked && cr == null) {
          cr = db2.createChildNode(db2.getRootNodeUUID(), "Instances", InstancesFolder.TYPE_NAME);
          crAdded = true;
        }
        try{
          NodeDesc nda = rs;
          //it is possible to define separate settings for specific project
          Map<TreeNode, String> nodes = RelManager.getLinks(reportS, false, GenerateReportHandler.ATTR_RELATED);
          if(genLinked && nodes!=null)
            for(TreeNode tn:nodes.keySet())
              if(tn.getType().equals(ReportSettings.TYPE_NAME)&&
                  tn.getTreeDB().getProjectName().equals(db2.getProjectName()))
              {
                nda = NodeDesc.fromNode(tn, true);
                nda.setId(nda.getId()+timeStamp);
                break;
              }
          if(db2.revision != null)
            RLogger.logError("");
          ReportSettings node = genLinked?(ReportSettings) nda.createNode(db2, cr.getUUId(), true):null;
          initGenerationContexts(handler, rgc.date, baseDB, db2, node, rgc.rootRData, monitor, contexts,
              tpl, relDBs, rgc.dbLinksReplace, rgc.relatedContexts, storedSlices, rgc.uriResolver);
          if(genLinked) {
            contexts.get(db2name).addToRemove(node);
            if(crAdded)
              contexts.get(db2name).addToRemove(cr);
          }
        }catch(Exception r){
          r.printStackTrace();
          if(crAdded)
            cr.delete();
        }
      }
    }
    monitor.doneTask();
    
    try{
      try {
        monitor.beginTask("get root - "+db.getProjectName(), 1);
        rgc.nodesCount = db.getStorage().getUCM().getNodesCount();
        //instTree.updateRequirementsNames(newroot);
      } catch (RuntimeException re) {
        re.printStackTrace();
        grh.openInformation("Report generator error", "Target Requirement does not exist.");
        return;
      }
      finally{
        monitor.doneTask();
      }
      rgc.savePath = rgc.reportData!=null?rgc.reportData.getNodeResourcesPath():null;
      try{
        rgc.reportRootReq = reportS==null?(Requirement) db.getNode(Requirement.getTypeRootQId()):reportS.getRootTargetRequirement(db);
        rgc.pageRootReq = rgc.reportRootReq;
        if(!db.inTransaction()){
          RMessages.openError("Report generation error", "Report cannot be generate because some transaction was not started "+db.getSemiProjectName(), db.getStorage().getVisibleName(), false);
          return;
        }

        try{
          monitor.beginTask("init main generation - "+db.getProjectName(), rgc.nodesCount);
          rgc.addedReportListener = new DummyReportNodeCacheListener(db, rgc.nodesCount, monitor);//TODO not db?
        }finally{
          monitor.doneTask();
        }
      }
      catch(Exception e){
        RLogger.logError(e);
      }
      // instTree.stopChangeRow();
    } catch (Exception e) {
      e.printStackTrace();
    }
    
  }
  public void updateVariable(AttributeDefinition def, Object value) {
    String key = def.getKey();
    if(!reportParameters.containsKey(key))
      reportParameters.put(key, value);
    if(!getFullReqsPropogateData().containsKey(key))
      reportPropData.put(key, def);
  }
  public void doneSingle(Exception e) {
    if(db!=null){
      if(addedReportListener!=null)
        db.getCache().removeListener(addedReportListener);
      RepoTracker rt = RepoTracker.getRepoTracker(db.getTracker());
      rt.deleteMarkedSlices();
      if(e!=null && reportData!=null){
        String startFile = null;
        for (TemplateFile tf:template.getTemplates()){
          String fileName = reportData.calcStartFileName(tf, reportS.getTreeDB(), null, this);
          if(startFile == null)
            startFile = fileName;
          String text = "<code>"+e.toString()+"\n"+CoreUtils.getTrace(e)+"</code>";
          try(InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))){
            rootRData.setResourceContent( rootRData.getStartFileName(tf, db, this),
              is, OFlag.NONE);
          } catch (IOException e1) {
            RLogger.logError(e1);
          }
        }
        if(reportData!=null && reportData == rootRData){
          reportData.attrs().setConstAttribute(ReportSettings.ATTR_LAST_GENERATED, startFile, 0);
          reportData.commitAttributes();
        }
      }
    }
    if(tempFold!=null){
      saveTempFilesToReport(rootRData, tempFold);
    }
  }
  public void doneProcess(Exception e) {
    generationStarted = false;
    if(db!=null){
      if(needsReportSFlagReset){
        reportS.setGenerating(false);
        reportS.commitAttributes();
      }
      if(addedReportListener!=null)
        db.getCache().removeListener(addedReportListener);

      if(e!=null){
        for (TemplateFile tf:template.getTemplates()){
          String text = "<code>"+e.toString()+"\n"+CoreUtils.getTrace(e)+"</code>";
          try(InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))){
            rootRData.setResourceContent( rootRData.getStartFileName(tf, db, this),
              is, OFlag.NONE);
          } catch (IOException e1) {
            RLogger.logError(e1);
          }
        }
      }
    }
    if(tempFold!=null && tempFold.exists()){
      saveTempFilesToReport(rootRData, tempFold);
      CoreUtils.delete(tempFold, true, false);
    }
    if(nodesToRemove!=null){
      reportS.delete(Flag.CHANGE_READ_ONLY);
      TreeNode insts = reportS.getWorkParent();
      if(insts!=null && insts.getId().equals("Instances") && insts.getWorkParent().isRoot())
        insts.delete();
      for(TreeNode insts2:nodesToRemove){
        if(insts2.isDisposed())
          continue;
        insts2.delete();
        if(insts2.getActiveWorkChildren().size() == 0)//remove instances folder
          insts2.delete();
      }
    }
    if(sources!=null)
      for(IReportDataProvider prov:sources.values())
        prov.generationOver(reportRootReq, null, this);//utils?
    //for residual contexts, genrationends will be required of some cases (esp. transaction ends and so on)
  //  if(reportData == rootRData){
      for(IReportStateListener rsl: statusListeners) 
        rsl.reportGenerationEnds(this, reportData);
      List<IReportGenerationParticipant> genParts = API.getReportGenerationParticipants();
      for(IReportGenerationParticipant genpart:genParts)
        genpart.reportGenerationEnds(this);
    //}
    if(reportData != null && db == baseDB)
      GenerateReportHandler.getOne().openReport(reportData);
  }

  public void preGenerationActions(){
    generationStarted = true;
    if(db!=null){
      RepoTracker rt = RepoTracker.getRepoTracker(db.getTracker());
      rt.deleteMarkedSlices();
    
      if(addedReportListener!=null)
        db.getCache().addListener(addedReportListener);
      monitor.beginTask("init params on generation root - "+db.getProjectName(), 1);
      if(reportData!=null) {
        TemplatesManager.getOne(db).saveReportSettings(reportData, template, this);
        reportData.attrs().setConstAttribute(ReportSettings.ATTR_DATE, date, 0);
        reportData.commitAttributes();
      }
      monitor.doneTask();
        
    }
  }
  public TreeDB getTree() {
    return db;
  }
  public Collection<TreeDB> getRelatedTrees(){
    return relatedTrees;
  }
  private Map<TreeDB, List<TreeNode>> savedMroots = new HashMap<>();
  public List<TreeNode> getMultipartRoots(TreeDB db) {
    List<TreeNode> ret = savedMroots.get(db);
    if(ret!=null)
      return ret;
    if(db == this.db) {
      MultiPart mp = mpart == null? db.getMultipartSettings():mpart;
      ret = mp.getMultipartRoots(this, reportRootReq, false);
    }else
    {
      ReportGenerationContext cont = relatedContexts.get(db.getLocalName());
      if(cont!=null)
        ret = cont.getMultipartRoots(db);
      else
        ret = db.getMultipartSettings().getMultipartRoots(null, (Requirement) db.getNode(Requirement.getTypeRootQId()), false);
    }
    savedMroots.put(db, ret);
    return ret;
  }
  private void saveTempFilesToReport(ReportSettings reportS, File tempFold) {
    saveTempFilesToReport(reportS, tempFold, "");
  }
  private void saveTempFilesToReport(ReportSettings reportS, File tempFold, String path) {
    File[] fls = tempFold.listFiles();
    if(fls!=null)
      for(File f:fls){
        String apath = path.isEmpty()?f.getName():path+"/"+f.getName();
        if(f.isFile())
          try(FileInputStream fis = new FileInputStream(f)){ 
            reportS.setResourceContent(apath, fis, OFlag.NONE);
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          } catch (IOException e) {
            //TODO Auto-generated catch block
            e.printStackTrace();
          }
        else {
          saveTempFilesToReport(reportS, f, apath);
        }
        f.delete();
      }
  }
  public ReportGenerationContext getRelatedContext(TreeDB db) {
    return relatedContexts == null ? null : relatedContexts.get(db.getLocalName());
  }

  public String getTemplFileName(TreeNode node){
    TreeDB db = node.getTreeDB();
    ReportGenerationContext cont2 = getRelatedContext(db);
    if(cont2 == null)
      cont2 = this;
    return cont2.getURIResolver().getTemplateFileName(db, ReportGenerationContext.getAdoptedReqRoot(cont2, db, node), cont2.reportData);//rev vers?
  }
  public ReportGenerationContext getRelatedContext(String name) {
    return relatedContexts == null ? null : relatedContexts.get(name);
  }
  public void addToRemove(TreeNode req) {
    if(nodesToRemove == null)
      nodesToRemove = new HashSet<>();
    nodesToRemove.add(req);
  }
  public void putAlteredData(String key, Object value) {
    reportParameters.put(key, value);
  }
  public Map<String, Object> getFullReportData(TreeNode mpRoot, TemplateFile tf){
    Map<String, Object> ret = new HashMap<>(reportParameters),
        alterData = null;
    Map<TemplateFile, Map<String, Object>> mp = nodeUtils.get(mpRoot.getUUId());
    if(mp != null)
      alterData = mp.get(tf);
    if(alterData != null)
      for(Entry<String, Object> en: alterData.entrySet()){
        if(!ret.containsKey(en.getKey()))
          ret.put(en.getKey(), en.getValue());
        else
          RLogger.logDebug("attribute definition duplication on different layers. Context.reportData version was used", 6);
      }
    return ret;
  }
  public MultiPart getMultiPart() {
    if(mpart!=null)
      return mpart;
    return db.getMultipartSettings();
  }
  public Map<String, AttributeDefinition> getFullReqsPropogateData() {
    if(reportPropData  == null || !reportPropDataInitedOnReport){
      reportPropData = new HashMap<>();
      SystemNames sn = SystemNames.getOne(db);
      if(reportData!=null) {
        reportPropDataInitedOnReport = true;
        for(Entry<String, Attribute> en:reportData.attrs().getActiveAttributes().entrySet())
          if(en.getValue()!=null){
            if(en.getValue().isUserVisible()){//remove non visible one
              AttributeDefinition ad = en.getValue().getDefinition(); 
              reportPropData.put(en.getKey(), new 
                  AttributeDefinition(ad.getOrigin(), ad.getKey(), AttributeAvailability.SUBTREE,
                      ad.getValueProvider(), ad.getFlags()|Flag.VIRTUAL));
            }
          }else{
            RLogger.logDebug("null value for attribute "+en.getKey()+" on "+reportData.getUUId()+" during generation");
          }
      }
      return reportPropData;
    }
    return reportPropData;
  }
  public static Set<TreeDB> getAllRelatedTreeDBs(ReportGenerationContext cont, TreeDB project) {
    ReportGenerationContext conta = cont.getRelatedContext(project);
    if(conta != null)
      return conta.relatedTrees;
    RLogger.logDebug("incorrect tree db call for getAllNodes and project "+project.getVisibleName());
    return Collections.emptySet();
  }
  public DefaultReportURIResolver getURIResolver() {
    return uriResolver;
  }
  public static TreeNode getAdoptedReqRoot(ReportGenerationContext context, TreeDB altDB, TreeNode altNode) {
    return getAdoptedReqRoot(context, context.multiRoots, altDB, altNode);
  }
  public static TreeNode getAdoptedReqRoot(ReportGenerationContext context, List<TreeNode>currentMpartSet, TreeDB altDB, TreeNode altNode) {
    if(altNode == null)
      return null;
    if(context == null)
      return null;
    MultiPart mpa = context.getMultiPart();
    List<TreeNode> aroots = null;
    String lname = altDB.getLocalName();
    String pname = altDB.getProjectName();
    if(context.db == altDB){
      aroots = currentMpartSet;
    }else{
      ReportGenerationContext relatedC = context;
      MultiPart mp = null;
      if(pname.equals(context.db.getProjectName()))
      {
        mp = context.getMultiPart();
      }else{
        relatedC = context.relatedContexts.get(lname);
        if(relatedC!=null)
          mp = relatedC.getMultiPart();
      }
      if(relatedC!=null){
        List<TreeNode> baseRoots = relatedC.multiRoots;
        if(baseRoots == null){
          if(relatedC!=null && mp!=null){
            TreeDB baseAltDb = context.baseDB.getTracker().getTreeDB(
              context.baseDB, lname, pname, true, context.monitor);
            aroots = mp.getMultipartRoots( 
              relatedC,
              (Requirement) baseAltDb.getNode(Requirement.getTypeRootQId()),
              true);
            relatedC.multiRoots = aroots;
          }
        }
        else
          aroots = baseRoots;
      }
    }
    if(aroots == null){
      RLogger.logDebug("Multipart roots initialization problem while loading of ("+altDB.getVisibleName()+")");
      return (Requirement) altDB.getNode(Requirement.getTypeRootQId());
    }
    Set<UUID> uuids = new HashSet<>();
    for(TreeNode node:aroots){
      if(node == null)
        continue;
      UUID uid = node.getUUId();
      if(!uuids.contains(uid))
        uuids.add(uid);
    }
    TreeNode par = altNode;;
    if(par == null)
      return (Requirement) aroots.iterator().next();//call out of roots, commonly on /Requirements
    while(par!=null && !uuids.contains(par.getUUId()))
      par = par.getWorkParent();
    if(par == null) {
      RLogger.logDebug("Node ("+altNode.getSUUId()+") out of multiline tree("+Arrays.toString(aroots.toArray())+"), fallback to first availiable root");
      if(aroots.isEmpty())
        return altDB.getNode(Requirement.getTypeRootQId());
      return (Requirement) aroots.iterator().next();//call out of roots, commonly on /Requirements
    }
    return par!=null?par: altDB.getNode(Requirement.getTypeRootQId());//TODO could return null? 
    //TODO non requirement root?
  }
  public boolean loadRelatedProjects() {
    boolean isGlobal = template.getParameterValue("allowsGlobal");
    if(isGlobal)
      return true;
  Object o = reportParameters.get(ReportSettings.ATTR_CP);
  return o!=null && o.toString().equals("true");
  }
  public ReportGenerationContext getBaseContext() {
    if(baseDB==db)
      return this;
    return getRelatedContext(baseDB);
  }
  public String getConstantStringValue(String variableName, TreeNode target) {
    switch(variableName.toUpperCase()) {
      case "PROJECT_ID_PREFIX":{
        return IDMechanics.getShownIdPrefix(target.getTreeDB());
      }
    }
  return null;
  }
  public TreeNode getPageRootReq() {
    return pageRootReq!=null?pageRootReq:rootRData;
  }
  public TreeNode detectRoot(TreeNode target) {
    List<UUID> roots = new LinkedList<>();
    for(TreeNode node:getMultipartRoots(target.getTreeDB()))
      roots.add(node.getUUId());
  return  detectRoot(target, roots);
  }
  private Map<UUID, TreeNode> mroots = new HashMap<>();
  private TreeNode detectRoot(TreeNode node, List<UUID> roots) {
    if(node == null)
      return null;
    if(roots.contains(node.getUUId()))
      return node;
    UUID uid = node.getUUId();
    TreeNode ret = mroots.get(uid);
    if(!mroots.containsKey(uid)) {//not calculated
      TreeNode mroot = detectRoot(node.getWorkParent(), roots);
      mroots.put(uid, mroot);
      ret = mroot;
    }
    return ret;
  }
  public boolean  isIgnorableRoot(TreeNode root) {
    return (!getMultiPart().isEnabled(root) || 
        detectRoot(root)!=root || 
        root.getQualifiedId().equals(Requirement.getTypeRootQId())) 
          && (reportParameters.getOrDefault(ReportSettings.ATTR_ROOT_INCLUDED, true)).toString().equals("false");
  }
  public ReportData getReportData() {
    if(reportData!=null)
      return reportData;
    return rootRData;
  }
  public TreeDB getTreeDB(TreeDB dbfrom, String localName, String projectName, IProgressMonitor monitor2) {
    for(TreeDB db:relatedTrees)
      if(db.getLocalName().equals(localName))
        return db;
    for(TreeDB db:relatedTrees)
      if(db.getProjectName().equals(projectName))
        return db;
    return baseTracker.getTreeDB(dbfrom, localName, projectName, true, monitor2);
  }
  
}
