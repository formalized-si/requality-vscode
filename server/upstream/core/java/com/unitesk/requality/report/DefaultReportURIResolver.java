package com.unitesk.requality.report;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.core.attribute.IReqURIResolver;
import com.unitesk.requality.core.attribute.ReqURIResolveResult;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.report.template.TemplateFile;

public class DefaultReportURIResolver implements IReqURIResolver{
  private TemplateFile tf = null;
  ReportGenerationContext context;
  public DefaultReportURIResolver(ReportGenerationContext context, TemplateFile t){
    tf = t;
    this.context = context;
  }
  private Map<TreeDB,Map<String, TreeDB>> savedTrees = new HashMap<>();
  @Override
  public ReqURIResolveResult resolve(TreeNode node, String path, String value) {
    int ind = path.indexOf("::");
    TreeNode trg = null;
    if(ind>-1) {
      String nodePath = path.substring(ind+2);
      String pname = path.substring(0, ind);
      TreeDB ndb = node.getTreeDB();
      TreeDB db = null;
      Map<String, TreeDB> map = savedTrees.get(ndb);
      if(map == null) {
        map = new HashMap<>();
        savedTrees.put(ndb, map);
      }
      db = map.get(pname);
      if(db == null) {
        db = ndb.getTracker()
        	  .getTreeDB(ndb,null, pname, false, ndb.getMonitor());//TODO IDE monitor?
        if(db!=null)
          map.put(pname, db);
      }
      if(db!=null)
        trg = db.getNode(nodePath);
      else 
        trg = node.getTreeDB().getNode(path);
    }else
      trg = node.getTreeDB().getNode(path);
    if(trg!=null){
      String did = trg.getForeignID();
      if(did!=null){
        TreeDB db = node.getTreeDB(),tdb = trg.getTreeDB();
        String nw = did;
        boolean tv = false;
        if(db!=tdb){
          if(Instantiator.getRootTree(db)!=Instantiator.getRootTree(tdb)){
            nw = trg.getExternalForeignID();
          }
        }
        if(value.equals(nw)){
          //nw = value;
          tv = true;
        }
        if(db!=tdb){
          if(Instantiator.getRootTree(db)!=Instantiator.getRootTree(tdb)){
//            String dname = tdb.getResName().toUpperCase();
//            if(tv)
//              nw = nw;
        	ReportSettings rs = context.reportS;//TODO check
            return new ReqURIResolveResult(getTemplateFileName(tdb, 
            		ReportGenerationContext.getAdoptedReqRoot(context, tdb, trg), rs)+"#"+did, nw, true);
          }
        }else {
        	//многотомная поддержка тут, возможно просто завернуть в detectName == detectName2
        }
        return new ReqURIResolveResult("#"+did, nw, true);
      }
    }
    return ReqURIResolveResult.NF;
  }
  private Map<String, String> templateFilename = new HashMap<>();
  public String getTemplateFileName(TemplateFile tf, TreeDB db, TreeNode rootReq, ReportSettings setts){
    String rootId = "root";
    if(rootReq!=null)
      rootId = rootReq.getSUUId();
    String id = db.getProjectName()+"-"+rootId+tf.getTemplatePath();
    String ret = templateFilename.get(id);
    if(ret == null){
      ret = tf.getOutputFileName(
    		  context.getReportData()==null?null:context.getReportData().getUserDefinedFileName(tf),
    		  db, rootReq, setts);
      templateFilename.put(id, ret);
    }
    return ret;
  }
  public String getTemplateFileName(TreeDB db, TreeNode rootReq, ReportSettings setts){
    return getTemplateFileName(tf, db, rootReq, setts);
  }
  public void setTemplateFile(TemplateFile tf){
    templateFilename.clear();
    this.tf = tf;
    
  }
}
